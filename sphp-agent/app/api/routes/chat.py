"""对话接口（系分 §6.2）。

前端直连 Agent 对话端点（SSE 流式）+ L2 确认回调。
"""

import json
import logging
import traceback
from collections.abc import AsyncIterator
from uuid import uuid4

from fastapi import APIRouter, Request
from fastapi.responses import StreamingResponse

from app.api.schemas.chat import ChatRequest, ConfirmRequest, ConfirmResponse
from app.orchestrator.graphs.main_graph import build_main_graph
from app.orchestrator.state import AgentState

logger = logging.getLogger(__name__)

router = APIRouter()

# 全局主图实例（编译后的 CompiledGraph）
_main_graph = None


def _get_graph():
    """懒加载主图（避免启动时 LangGraph 依赖未就绪）。"""
    global _main_graph
    if _main_graph is None:
        _main_graph = build_main_graph()
    return _main_graph


@router.post("/chat/stream")
async def chat_stream(req: ChatRequest, request: Request) -> StreamingResponse:
    """对话主接口（系分 §6.2.1，SSE 流式）。

    前端发起对话 -> JWT 鉴权 -> LLM 推理 -> SSE 流式返回。
    """
    token = getattr(request.state, "jwt_token", None)
    trace_id = getattr(request.state, "trace_id", "")
    initial_state = _build_initial_state(req, request, token)

    return StreamingResponse(
        _sse_generator(initial_state, req.session_id, trace_id),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "Connection": "keep-alive"},
    )


def _build_initial_state(req: ChatRequest, request: Request, token: str | None) -> AgentState:
    """构造初始状态（不依赖外部 mutation）。"""
    return {
        "messages": [{"role": "user", "content": req.content}],
        "session_id": req.session_id,
        "intent": None,
        "user_id": getattr(request.state, "user_id", None),
        "scope": getattr(request.state, "scope", req.scope),
        "roles": None,
        "dept_id": None,
        "doctor_id": None,
        "hospital_id": (req.context or {}).get("hospital_id"),
        "tool_calls": None,
        "tool_results": None,
        "pending_confirmation": None,
        "risk_flags": [],
        "jwt_token": token,
    }


async def _sse_generator(
    initial_state: AgentState, session_id: str | None, trace_id: str
) -> AsyncIterator[str]:
    """SSE 流式输出生成器（系分 §6.2.1，基于 astream_events v2）。

    - on_chat_model_stream（reply_node）-> message 事件，逐 token 推送
    - on_tool_start / on_tool_end -> action / observation 事件
    - on_chain_end（reply_node）-> 未流式时兜底推送完整回复
    - 结束 -> done 事件
    """
    graph = _get_graph()
    thread_id = session_id or str(uuid4())
    config = {"configurable": {"thread_id": thread_id}}

    logger.info("[SSE] 开始流程执行, thread_id=%s", thread_id)
    streamed_reply = False

    try:
        async for event in graph.astream_events(initial_state, config=config, version="v2"):
            kind = event.get("event", "")
            name = event.get("name", "")
            data = event.get("data", {}) or {}
            node = (event.get("metadata") or {}).get("langgraph_node", "")

            # LLM 逐 token 流式输出（仅 reply_node，过滤 intent_node 的分类输出）
            if kind == "on_chat_model_stream" and node == "reply_node":
                chunk = data.get("chunk")
                delta = getattr(chunk, "content", "") if chunk else ""
                if delta:
                    streamed_reply = True
                    yield _sse("message", {"delta": delta})

            # 工具调用事件
            elif kind == "on_tool_start":
                yield _sse("action", {"tool": name})
            elif kind == "on_tool_end":
                yield _sse("observation", {"tool": name})

            # 兜底：reply_node 完成但未流式时，推送完整回复
            elif kind == "on_chain_end" and node == "reply_node" and not streamed_reply:
                content = _extract_last_content(data.get("output"))
                if content:
                    yield _sse("message", {"delta": content})

        logger.info("[SSE] 流程完成, session_id=%s", thread_id)
        yield _sse("done", {"session_id": thread_id, "trace_id": trace_id})

    except Exception as e:
        logger.error("[SSE] 异常: %s\n%s", e, traceback.format_exc())
        # 不向客户端泄露内部异常细节
        yield _sse(
            "error",
            {"code": "SERVER_ERROR", "message": "服务异常，请稍后重试", "trace_id": trace_id},
        )
        yield _sse("done", {})


def _sse(event: str, payload: dict) -> str:
    """构造一条 SSE 事件。"""
    return f"event: {event}\ndata: {json.dumps(payload, ensure_ascii=False)}\n\n"


def _extract_last_content(output: object) -> str:
    """从节点输出中提取最后一条消息的文本。"""
    if not isinstance(output, dict):
        return ""
    messages = output.get("messages", [])
    if not messages:
        return ""
    last = messages[-1]
    if isinstance(last, dict):
        return last.get("content", "")
    return getattr(last, "content", "")


@router.post("/chat/confirm", response_model=ConfirmResponse)
async def chat_confirm(req: ConfirmRequest, request: Request) -> ConfirmResponse:
    """L2 确认回调（系分 §6.2.2）。

    前端用户点击确认卡片后回调此端点。
    Agent 校验 confirm_token，通过后继续执行对应的 MCP 工具。

    ⚠️ 未实现：confirm_token 校验与 L2 工具执行（待后续完善）。
    """
    trace_id = getattr(request.state, "trace_id", "")
    # TODO: 从 Redis 获取并删除 confirm_token（一次性消费）
    # TODO: 校验通过后继续执行 MCP 工具，通过 SSE 推送结果
    return ConfirmResponse(code="00000", message="确认成功，正在处理", data=None, traceId=trace_id)
