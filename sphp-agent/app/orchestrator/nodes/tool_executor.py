"""工具执行节点（系分 §5.3.1 工具分发逻辑）。

从 engine/tools/executor.py 迁移而来，按系分要求移到编排层。
负责区分 MCP 工具和本地工具，走不同执行路径。
"""

import asyncio
import hashlib
import json
import time

from app.engine.tools.schema_registry import ToolRegistry
from app.infrastructure.audit.logger import log_tool_call
from app.orchestrator.state import AgentState


async def tool_executor(state: AgentState) -> dict:
    """执行已通过安全校验的工具调用。

    MCP 工具：通过 MCP Client 调用 tools/call
    本地工具：直接执行，跳过 MCP 层

    多个独立工具调用使用 asyncio.gather() 并发执行。
    """
    tool_calls = state.get("tool_calls") or []
    if not tool_calls:
        return {"tool_results": []}

    tasks = []
    for tc in tool_calls:
        tool_name = tc.get("name", "")
        arguments = tc.get("arguments", {})
        if ToolRegistry.is_local(tool_name):
            tasks.append(_execute_local(tool_name, arguments, state))
        else:
            tasks.append(_execute_mcp(tool_name, arguments, state))

    results = await asyncio.gather(*tasks, return_exceptions=True)

    # 包装异常为错误结果
    formatted = []
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

    return {"tool_results": formatted}


async def _execute_mcp(tool_name: str, arguments: dict, state: AgentState) -> dict:
    """通过 MCP Client 调用 MCP Server 执行工具。"""
    start = time.time()
    user_id = state.get("user_id")

    # TODO: 接入 MCP Client → tools/call
    # 当前占位：直接调 Java REST API（过渡实现）
    from app.infrastructure.java_client import call_java_api

    try:
        result = await call_java_api(
            tool_name=tool_name,
            arguments=arguments,
            user_id=user_id,
            scope=state.get("scope", "c_end"),
        )
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
        results = search_knowledge(query=query)
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
        user_id=str(state.get("user_id", "")),
        tool_name=tool_name,
        params_hash=params_hash,
        result=result,
        duration_ms=duration_ms,
    )
