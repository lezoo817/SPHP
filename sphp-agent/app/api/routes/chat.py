"""对话接口（系分 §6.2）。

前端直连 Agent 对话端点（SSE 流式）+ L2 确认回调。
合并了原 confirm.py 的确认端点。
"""

import json
import logging

from fastapi import APIRouter, Request
from fastapi.responses import StreamingResponse

from app.api.schemas.chat import ChatRequest, ConfirmRequest, ConfirmResponse, ErrorResponse
from app.orchestrator.state import AgentState
from app.orchestrator.graphs.main_graph import build_main_graph

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
async def chat_stream(req: ChatRequest, request: Request):
    """对话主接口（系分 §6.2.1，SSE 流式）。

    前端发起对话 → JWT 鉴权 → LLM 推理 → SSE 流式返回。
    """
    # 从中间件获取 JWT token 和 trace_id
    token = getattr(request.state, "jwt_token", None)
    trace_id = getattr(request.state, "trace_id", "")

    # 构造初始状态（jwt_token 注入到状态中，供 auth_node 使用）
    initial_state: AgentState = {
        "messages": [],
        "session_id": req.session_id,
        "intent": None,
        "user_id": None,
        "scope": req.scope,
        "roles": None,
        "dept_id": None,
        "doctor_id": None,
        "hospital_id": (req.context or {}).get("hospital_id"),
        "tool_calls": None,
        "tool_results": None,
        "pending_confirmation": None,
        "risk_flags": [],
        "jwt_token": token,  # 注入 JWT token，供 auth_node 调用 Java token/parse
    }

    async def sse_generator():
        """SSE 流式输出生成器（系分 §6.2.1）。

        接入 LangGraph astream_events，映射为 7 类 SSE 事件。
        """
        try:
            graph = _get_graph()

            # LangGraph 需要thread_id来管理会话状态
            from uuid import uuid4
            thread_id = req.session_id or str(uuid4())

            # 配置会话
            config = {
                "configurable": {
                    "thread_id": thread_id
                }
            }

            # LangGraph astream_events 流式输出
            async for event in graph.astream_events(
                initial_state,
                config=config,
                version="v2",
            ):
                # 映射 SSE 事件
                event_kind = event.get("event")

                if event_kind == "on_chain_start":
                    # 节点开始执行
                    node_name = event.get("name")
                    logger.debug("节点开始: %s", node_name)

                elif event_kind == "on_chain_end":
                    # 节点执行完成
                    node_name = event.get("name")
                    output = event.get("data", {}).get("output", {})
                    logger.debug("节点完成: %s, 输出: %s", node_name, output)

                elif event_kind == "on_chat_model_stream":
                    # LLM 流式 token
                    chunk = event.get("data", {}).get("chunk")
                    if chunk and hasattr(chunk, "content"):
                        delta = chunk.content
                        yield f"event: message\ndata: {json.dumps({'delta': delta}, ensure_ascii=False)}\n\n"

                elif event_kind == "on_tool_start":
                    # 工具调用开始
                    tool_name = event.get("name")
                    yield f"event: action\ndata: {json.dumps({'tool': tool_name}, ensure_ascii=False)}\n\n"

                elif event_kind == "on_tool_end":
                    # 工具调用完成
                    tool_output = event.get("data", {}).get("output", {})
                    yield f"event: observation\ndata: {json.dumps({'result': tool_output}, ensure_ascii=False)}\n\n"

            # 推送 done 事件
            yield f"event: done\ndata: {json.dumps({'session_id': req.session_id or '', 'trace_id': trace_id}, ensure_ascii=False)}\n\n"

        except Exception as e:
            logger.exception("SSE stream error: %s", str(e))
            yield f"event: error\ndata: {json.dumps({'code': 'SERVER_ERROR', 'message': '服务异常，请稍后重试', 'trace_id': trace_id}, ensure_ascii=False)}\n\n"
            yield f"event: done\ndata: {json.dumps({}, ensure_ascii=False)}\n\n"

    return StreamingResponse(
        sse_generator(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "Connection": "keep-alive"},
    )


@router.post("/chat/confirm", response_model=ConfirmResponse)
async def chat_confirm(req: ConfirmRequest, request: Request) -> ConfirmResponse:
    """L2 确认回调（系分 §6.2.2）。

    前端用户点击确认卡片后回调此端点。
    Agent 校验 confirm_token，通过后继续执行对应的 MCP 工具。
    """
    trace_id = getattr(request.state, "trace_id", "")

    # TODO: 从 Redis 获取并删除 confirm_token（一次性消费）
    # from app.infrastructure.cache.redis_client import get_and_delete_confirm_token
    # token_data = await get_and_delete_confirm_token(...)
    # if token_data is None:
    #     return ConfirmResponse(code="CONFIRM_EXPIRED", message="确认已超时，请重新发起操作", data=None, traceId=trace_id)

    # TODO: 校验通过后继续执行 MCP 工具，通过 SSE 推送结果

    return ConfirmResponse(code="00000", message="确认成功，正在处理", data=None, traceId=trace_id)
