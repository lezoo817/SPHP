"""工具执行节点（系分 §5.3.1 工具分发逻辑）。

从 engine/tools/executor.py 迁移而来，按系分要求移到编排层。
负责区分 MCP 工具和本地工具，走不同执行路径。

工具可见性（系分 §5.12，M6-A1 定案）：
    action/observation 事件由 SSE 层从子图最终 state 的 ``tool_results`` 字段
    重建（langgraph 1.2.9 子图内 custom 事件不传播到主图），本节点不推送
    自定义事件，仅返回 ``tool_results`` 供 SSE 层消费。

M6-C1 执行链路：MCP 工具经 ``MCP Client -> tools/call -> MCP Server ->
dispatcher`` 调用封装函数；MCP Client 不可用时回退 dispatcher 直调。
工具名 -> 封装函数映射已迁至 app/mcp_server/tools/dispatcher.py。
"""

import asyncio
import hashlib
import json
import logging
import time
from typing import Any, cast

from app.engine.tools.schema_registry import ToolRegistry
from app.infrastructure.audit.logger import log_tool_call
from app.mcp_client.client import MCPClientError, get_mcp_client
from app.mcp_server.tools.dispatcher import dispatch_tool, is_registered
from app.orchestrator.state import AgentState

logger = logging.getLogger(__name__)


async def _call_mcp_func(
    tool_name: str, arguments: dict[str, Any], user_id: int | None
) -> dict[str, Any]:
    """执行 MCP 工具（M6-C1：优先走 MCP Client，不可用时回退直调）。

    仅当 MCP Client 连接层不可用时回退直调封装函数；工具执行失败（Server 侧
    异常）以普通异常上抛，由 _execute_mcp 捕获为 TOOL_FAILED，不触发回退，
    避免同一工具被执行两次。
    """
    try:
        client = await get_mcp_client()
        return await client.call_tool(tool_name, arguments, user_id)
    except MCPClientError as e:
        logger.warning("MCP Client 不可用(%s)，回退直调工具: %s", e, tool_name)
        return await dispatch_tool(tool_name, arguments, user_id)


async def tool_executor(state: AgentState) -> dict[str, Any]:
    """执行已通过安全校验的工具调用。

    MCP 工具：通过 MCP Client 调用 tools/call
    本地工具：直接执行，跳过 MCP 层

    多个独立工具调用使用 asyncio.gather() 并发执行；action/observation
    事件由 SSE 层从 tool_results 反推重建（M6-A1 定案）。
    """
    tool_calls = state.get("tool_calls") or []
    if not tool_calls:
        # 无工具调用时不更新 tool_results（返回空 dict），避免覆盖
        # 子图循环上一轮已获取的查询结果，保证 reply 能基于真实数据生成
        return {}

    tasks = []
    for tc in tool_calls:
        tool_name = tc.get("name", "")
        arguments = tc.get("arguments", {})
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
        arguments = tool_calls[i].get("arguments", {})
        if isinstance(result, Exception):
            formatted.append(
                {
                    "tool_name": tool_calls[i].get("name", ""),
                    "success": False,
                    # P2 脱敏：str(e) 可能含内部路径/连接串，详情仅日志
                    "error": {"code": "TOOL_FAILED", "message": _safe_error_message(result)},
                    # 携带参数，供 SSE 层反推 action 事件（子图循环会覆盖 tool_calls）
                    "arguments": arguments,
                    "duration_ms": 0,
                }
            )
        else:
            # 不可变合并：原结果 + 参数（供 SSE 层反推 action 事件）。
            # 非 Exception 分支下 result 必为封装函数返回的 dict（BaseException
            # 仅当任务抛出非 Exception 异常才可达，理论不发生），cast 消除联合类型
            formatted.append({**cast(dict[str, Any], result), "arguments": arguments})

    # 自累积：保留子图循环前面轮次的执行结果（tool_results 无 reducer，
    # 默认 last-write-wins 会覆盖多轮 L1 结果）。
    # 不用 Annotated[list, operator.add] reducer：checkpointer 按 session 持久化，
    # reducer 语义下新一轮对话传入 tool_results=None 会被追加到旧结果而非重置；
    # 自累积只发生在单次对话的子图循环内部，跨轮对话由 _build_initial_state
    # 传 None（last-write-wins）正常清场，历史结果不会污染下一轮。
    previous = state.get("tool_results") or []
    return {"tool_results": previous + formatted}


def _safe_error_message(exc: BaseException) -> str:
    """构造对外的工具失败提示（P2 脱敏）。

    ``str(exc)`` 可能含内部文件路径、连接串、SQL 等敏感细节，直接透传给
    LLM / SSE observation 会泄露系统内部结构。因此详情仅记录到服务端日志
    （含异常类型与信息），对外统一返回通用提示，不暴露任何内部信息。

    Args:
        exc: 工具执行抛出的异常。

    Returns:
        str: 对外脱敏后的通用错误提示。
    """
    logger.error("工具执行异常（详情仅日志，类型=%s）: %s", type(exc).__name__, exc)
    return "工具执行失败，请稍后重试"


def _classify_tool_result(result: dict[str, Any]) -> tuple[bool, dict[str, Any] | None]:
    """判断工具返回是否失败，返回 ``(是否失败, error)``（P1-1）。

    封装函数直连 call_java_api，返回两种结构：

    - Java 统一信封 ``{code, message, data, traceId}``：``code != "00000"``
      视为业务失败（如 DEPT_NOT_FOUND / JAVA_404）；
    - call_java_api 包装的连接/超时/解析失败 ``{success: False, error: {...}}``
      （HTTP 5xx / 超时 / 非 JSON 响应）。

    成功（含无信封字段的裸 dict，保持兼容）返回 ``(False, None)``；失败返回
    ``(True, {"code": ..., "message": ...})``。

    Args:
        result: 封装函数返回的 dict。

    Returns:
        (是否失败, error)；error 为 None 表示成功。
    """
    # call_java_api 包装的连接/超时/解析失败
    if result.get("success") is False:
        err = result.get("error") or {}
        return True, {
            "code": err.get("code", "TOOL_FAILED"),
            "message": err.get("message", "工具调用失败"),
        }
    # Java 统一信封业务失败（code 存在且非成功码 "00000"）
    code = result.get("code")
    if code is not None and code != "00000":
        return True, {
            "code": str(code),
            "message": result.get("message") or "业务处理失败",
        }
    return False, None


async def _execute_mcp(
    tool_name: str, arguments: dict[str, Any], state: AgentState
) -> dict[str, Any]:
    """执行 MCP 工具（M6-C1：经 MCP Client tools/call，回退直调）。

    工具名 -> 封装函数的映射见 dispatcher._MCP_TOOL_FUNCS。
    执行异常统一捕获为 TOOL_FAILED，不影响其余并发工具。
    """
    start = time.time()
    user_id = state.get("user_id")

    if not is_registered(tool_name):
        duration_ms = (time.time() - start) * 1000
        _log_audit(state, tool_name, arguments, "failed", duration_ms)
        return {
            "tool_name": tool_name,
            "success": False,
            "error": {"code": "UNKNOWN_TOOL", "message": f"未知的 MCP 工具: {tool_name}"},
            # P1 契约对齐：携带执行耗时供 SSE observation.duration_ms
            "duration_ms": round(duration_ms),
        }

    try:
        result = await _call_mcp_func(tool_name, arguments, user_id)
        duration_ms = (time.time() - start) * 1000
        # 安全（P1-1）：校验 Java 信封 / call_java_api 失败包装，避免 5xx 或
        # 业务失败（code != "00000"）被伪装为 success=True，否则 confirm 会回
        # "操作成功"、审计误记 success，直接误导用户与运营。
        failed, error = _classify_tool_result(result)
        _log_audit(state, tool_name, arguments, "failed" if failed else "success", duration_ms)
        if failed:
            return {
                "tool_name": tool_name,
                "success": False,
                "error": cast(dict[str, Any], error),
                "duration_ms": round(duration_ms),
            }
        return {
            "tool_name": tool_name,
            "success": True,
            "data": result,
            "duration_ms": round(duration_ms),
        }
    except Exception as e:
        duration_ms = (time.time() - start) * 1000
        _log_audit(state, tool_name, arguments, "failed", duration_ms)
        return {
            "tool_name": tool_name,
            "success": False,
            # P2 脱敏：str(e) 可能含内部文件路径/连接串，详情仅日志
            "error": {"code": "TOOL_FAILED", "message": _safe_error_message(e)},
            "duration_ms": round(duration_ms),
        }


async def _execute_local(
    tool_name: str, arguments: dict[str, Any], state: AgentState
) -> dict[str, Any]:
    """执行本地工具（pgvector 检索），不经 MCP Server。"""
    start = time.time()

    if tool_name in ("search_medical_knowledge", "interpret_report"):
        from app.engine.rag.search import search_knowledge

        query = arguments.get("query") or arguments.get("report_content", "")
        results = await search_knowledge(query=query)
        duration_ms = (time.time() - start) * 1000
        _log_audit(state, tool_name, arguments, "success", duration_ms)
        return {
            "tool_name": tool_name,
            "success": True,
            "data": {"results": results},
            "duration_ms": round(duration_ms),
        }

    duration_ms = (time.time() - start) * 1000
    _log_audit(state, tool_name, arguments, "failed", duration_ms)
    return {
        "tool_name": tool_name,
        "success": False,
        "error": {"code": "UNKNOWN_TOOL", "message": f"未知的本地工具: {tool_name}"},
        "duration_ms": round(duration_ms),
    }


def _log_audit(
    state: AgentState,
    tool_name: str,
    arguments: dict[str, Any],
    result: str,
    duration_ms: float,
) -> None:
    """记录审计日志。"""
    params_hash = hashlib.sha256(json.dumps(arguments, sort_keys=True).encode()).hexdigest()[:16]
    log_tool_call(
        session_id=state.get("session_id") or "",
        # None（匿名）记为空串而非 "None"，保持审计一致性
        user_id=str(state.get("user_id") or ""),
        tool_name=tool_name,
        params_hash=params_hash,
        result=result,
        duration_ms=duration_ms,
    )


def _make_failure(
    state: AgentState,
    tool_name: str,
    arguments: dict[str, Any],
    code: str,
    message: str,
) -> dict[str, Any]:
    """构造工具失败结果。"""
    _log_audit(state, tool_name, arguments, "failed", 0)
    return {
        "tool_name": tool_name,
        "success": False,
        "error": {"code": code, "message": message},
        "duration_ms": 0,
    }


async def _async_failure(
    state: AgentState,
    tool_name: str,
    arguments: dict[str, Any],
    code: str,
    message: str,
) -> dict[str, Any]:
    """异步包装 _make_failure，兼容 asyncio.gather 签名。"""
    return _make_failure(state, tool_name, arguments, code, message)
