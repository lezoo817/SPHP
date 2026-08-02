"""工具执行节点（系分 §5.3.1 工具分发逻辑）。

从 engine/tools/executor.py 迁移而来，按系分要求移到编排层。
负责区分 MCP 工具和本地工具，走不同执行路径。

工具可见性（系分 §5.12）：
    通过 ``get_stream_writer()`` 推送 ``tool_action`` / ``tool_observation``
    自定义事件，SSE 层在 ``astream(stream_mode=["custom", ...])`` 中接收，
    映射为 ``event: action`` / ``event: observation`` 推送给前端。
"""

import asyncio
import hashlib
import json
import time
from typing import Any

from langgraph.config import get_stream_writer

from app.engine.tools.schema_registry import ToolRegistry
from app.infrastructure.audit.logger import log_tool_call
from app.orchestrator.state import AgentState


async def _wrap(module: Any, func_name: str, arguments: dict, user_id: int | None) -> dict:
    """按参数名绑定调用 MCP 工具封装函数（忽略未知参数）。"""
    func = getattr(module, func_name)
    import inspect

    sig = inspect.signature(func)
    kwargs = {k: v for k, v in arguments.items() if k in sig.parameters}
    if "user_id" in sig.parameters:
        kwargs["user_id"] = user_id
    return await func(**kwargs)


# 工具名 -> (模块, 函数名) 映射（过渡实现：直接调封装函数 → Java REST）
# 对应 mcp_server/tools/*.py 中已实现的封装函数，签名与 ToolSchema 参数一致。
_MCP_TOOL_MODULES = {
    "triage": "app.mcp_server.tools.triage",
    "appointment": "app.mcp_server.tools.appointment",
    "consultation": "app.mcp_server.tools.consultation",
    "health": "app.mcp_server.tools.health",
    "notification": "app.mcp_server.tools.notification",
    "pharmacy": "app.mcp_server.tools.pharmacy",
    "prescription": "app.mcp_server.tools.prescription",
    "b_doctor": "app.mcp_server.tools.b_doctor",
}

# 工具名 -> (模块 key, 函数名)
_MCP_TOOL_FUNCS: dict[str, tuple[str, str]] = {
    # 导诊
    "create_triage_assessment": ("triage", "create_triage_assessment"),
    # 挂号查询
    "query_departments": ("appointment", "query_departments"),
    "query_doctors": ("appointment", "query_doctors"),
    "query_schedule_slots": ("appointment", "query_schedule_slots"),
    # 挂号订单
    "create_appointment": ("appointment", "create_appointment"),
    "query_appointments": ("appointment", "query_appointments"),
    "cancel_appointment": ("appointment", "cancel_appointment"),
    "join_waitlist": ("appointment", "join_waitlist"),
    "query_payment_status": ("appointment", "query_payment_status"),
    # 问诊
    "save_pre_consultation": ("consultation", "save_pre_consultation"),
    "query_consultations": ("consultation", "query_consultations"),
    "send_consultation_message": ("consultation", "send_consultation_message"),
    # 处方
    "query_prescriptions": ("prescription", "query_prescriptions"),
    "interpret_prescription": ("prescription", "interpret_prescription"),
    # 购药
    "query_pharmacy_stock": ("pharmacy", "query_pharmacy_stock"),
    "create_drug_order": ("pharmacy", "create_drug_order"),
    "query_drug_orders": ("pharmacy", "query_drug_orders"),
    "cancel_drug_order": ("pharmacy", "cancel_drug_order"),
    "confirm_drug_receipt": ("pharmacy", "confirm_drug_receipt"),
    # 健康管理
    "query_health_record": ("health", "query_health_record"),
    "manage_allergy": ("health", "manage_allergy"),
    "manage_medical_history": ("health", "manage_medical_history"),
    "query_reports": ("health", "query_reports"),
    "create_report": ("health", "create_report"),
    "query_medication_plans": ("health", "query_medication_plans"),
    "update_medication_plan": ("health", "update_medication_plan"),
    "query_follow_ups": ("health", "query_follow_ups"),
    "confirm_follow_up": ("health", "confirm_follow_up"),
    "manage_notifications": ("notification", "manage_notifications"),
    # B 端
    "query_patient_history": ("b_doctor", "query_patient_history"),
    "query_drug_guide": ("b_doctor", "query_drug_guide"),
    "check_drug_interaction": ("b_doctor", "check_drug_interaction"),
    "generate_draft_note": ("b_doctor", "generate_draft_note"),
    "recommend_care": ("b_doctor", "recommend_care"),
    "check_contraindication": ("b_doctor", "check_contraindication"),
    "check_allergy_risk": ("b_doctor", "check_allergy_risk"),
    "check_duplicate_medication": ("b_doctor", "check_duplicate_medication"),
}


async def _call_mcp_func(tool_name: str, arguments: dict, user_id: int | None) -> dict:
    """按 _MCP_TOOL_FUNCS 映射调用封装函数（返回 Java 响应 dict）。"""
    module_key, func_name = _MCP_TOOL_FUNCS[tool_name]
    import importlib

    module = importlib.import_module(_MCP_TOOL_MODULES[module_key])
    return await _wrap(module, func_name, arguments, user_id)


async def tool_executor(state: AgentState) -> dict:
    """执行已通过安全校验的工具调用。

    MCP 工具：通过 MCP Client 调用 tools/call
    本地工具：直接执行，跳过 MCP 层

    每个工具调用前后推送 tool_action / tool_observation 事件，
    多个独立工具调用使用 asyncio.gather() 并发执行。
    """
    tool_calls = state.get("tool_calls") or []
    if not tool_calls:
        # 无工具调用时不更新 tool_results（返回空 dict），避免覆盖
        # 子图循环上一轮已获取的查询结果，保证 reply 能基于真实数据生成
        return {}

    writer = get_stream_writer()

    tasks = []
    for tc in tool_calls:
        tool_name = tc.get("name", "")
        arguments = tc.get("arguments", {})
        # 推送 action 事件（工具调用开始）
        writer({"type": "tool_action", "tool": tool_name, "arguments": arguments})

        # 未注册工具：直接生成失败结果，不抛异常
        if ToolRegistry.get_tool(tool_name) is None:
            tasks.append(
                _async_failure(
                    state, tool_name, arguments, "UNKNOWN_TOOL", f"未注册的工具: {tool_name}"
                )
            )
            continue

        if ToolRegistry.is_local(tool_name):
            tasks.append(_execute_local(tool_name, arguments, state))
        else:
            tasks.append(_execute_mcp(tool_name, arguments, state))

    results = await asyncio.gather(*tasks, return_exceptions=True)

    # 包装异常为错误结果
    formatted: list[dict[str, Any]] = []
    for i, result in enumerate(results):
        if isinstance(result, Exception):
            formatted.append(
                {
                    "tool_name": tool_calls[i].get("name", ""),
                    "success": False,
                    "error": {"code": "TOOL_FAILED", "message": str(result)},
                }
            )
        else:
            formatted.append(result)
        # 推送 observation 事件（工具返回结果）
        writer({"type": "tool_observation", "result": formatted[-1]})

    return {"tool_results": formatted}


async def _execute_mcp(tool_name: str, arguments: dict, state: AgentState) -> dict:
    """执行 MCP 工具（过渡实现：直接调 MCP 工具封装函数 → Java REST）。

    工具名 -> 封装函数的映射见 ``_MCP_TOOL_FUNCS``。
    未来接入 MCP Client 后，改为通过 ``tools/call`` 调用，本函数保留分发骨架。
    """
    start = time.time()
    user_id = state.get("user_id")

    if tool_name not in _MCP_TOOL_FUNCS:
        duration_ms = (time.time() - start) * 1000
        _log_audit(state, tool_name, arguments, "failed", duration_ms)
        return {
            "tool_name": tool_name,
            "success": False,
            "error": {"code": "UNKNOWN_TOOL", "message": f"未知的 MCP 工具: {tool_name}"},
        }

    try:
        result = await _call_mcp_func(tool_name, arguments, user_id)
        duration_ms = (time.time() - start) * 1000
        _log_audit(state, tool_name, arguments, "success", duration_ms)
        return {"tool_name": tool_name, "success": True, "data": result}
    except Exception as e:
        duration_ms = (time.time() - start) * 1000
        _log_audit(state, tool_name, arguments, "failed", duration_ms)
        return {
            "tool_name": tool_name,
            "success": False,
            "error": {"code": "TOOL_FAILED", "message": str(e)},
        }


async def _execute_local(tool_name: str, arguments: dict, state: AgentState) -> dict:
    """执行本地工具（pgvector 检索），不经 MCP Server。"""
    start = time.time()

    if tool_name in ("search_medical_knowledge", "interpret_report"):
        from app.engine.rag.search import search_knowledge

        query = arguments.get("query") or arguments.get("report_content", "")
        results = await search_knowledge(query=query)
        duration_ms = (time.time() - start) * 1000
        _log_audit(state, tool_name, arguments, "success", duration_ms)
        return {"tool_name": tool_name, "success": True, "data": {"results": results}}

    _log_audit(state, tool_name, arguments, "failed", 0)
    return {
        "tool_name": tool_name,
        "success": False,
        "error": {"code": "UNKNOWN_TOOL", "message": f"未知的本地工具: {tool_name}"},
    }


def _log_audit(
    state: AgentState, tool_name: str, arguments: dict, result: str, duration_ms: float
) -> None:
    """记录审计日志。"""
    params_hash = hashlib.sha256(json.dumps(arguments, sort_keys=True).encode()).hexdigest()[:16]
    log_tool_call(
        session_id=state.get("session_id", ""),
        # None（匿名）记为空串而非 "None"，保持审计一致性
        user_id=str(state.get("user_id") or ""),
        tool_name=tool_name,
        params_hash=params_hash,
        result=result,
        duration_ms=duration_ms,
    )


def _make_failure(
    state: AgentState, tool_name: str, arguments: dict, code: str, message: str
) -> dict:
    """构造工具失败结果。"""
    _log_audit(state, tool_name, arguments, "failed", 0)
    return {
        "tool_name": tool_name,
        "success": False,
        "error": {"code": code, "message": message},
    }


async def _async_failure(
    state: AgentState, tool_name: str, arguments: dict, code: str, message: str
) -> dict:
    """异步包装 _make_failure，兼容 asyncio.gather 签名。"""
    return _make_failure(state, tool_name, arguments, code, message)
