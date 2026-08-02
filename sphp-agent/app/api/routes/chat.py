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
    token = getattr(request.state, "jwt_token", None) if hasattr(request, "state") else None
    trace_id = getattr(request.state, "trace_id", "") if hasattr(request, "state") else ""

    # 构造初始状态（jwt_token 注入到状态中，供 auth_node 使用）
    initial_state: AgentState = {
        "messages": [],
        "session_id": req.session_id,
        "intent": None,
        "user_id": None,
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

    async def sse_generator():
        """SSE 流式输出生成器（系分 §6.2.1）。"""
        try:
            graph = _get_graph()
            from uuid import uuid4

            thread_id = req.session_id or str(uuid4())
            config = {"configurable": {"thread_id": thread_id}}

            # 添加用户消息
            initial_state["messages"] = [{"role": "user", "content": req.content}]

            logger.info(f"[SSE] 开始流程执行, thread_id={thread_id}")

            # 执行LangGraph流程
            final_state = None
            async for chunk in graph.astream(initial_state, config=config):
                for node_name, output in chunk.items():
                    logger.debug(f"[SSE] 节点完成: {node_name}")

                    # 推送节点执行事件
                    yield f"event: thought\ndata: {json.dumps({'node': node_name}, ensure_ascii=False)}\n\n"

                    # 保存最终状态
                    final_state = output

            # 推送最终消息
            if final_state and "messages" in final_state:
                messages = final_state.get("messages", [])
                if messages:
                    last_msg = messages[-1]
                    if isinstance(last_msg, dict) and "content" in last_msg:
                        content = last_msg["content"]
                        logger.info(f"[SSE] 推送回复, 长度={len(content)}")
                        yield f"event: message\ndata: {json.dumps({'delta': content}, ensure_ascii=False)}\n\n"

            # 完成
            logger.info(f"[SSE] 流程完成, session_id={thread_id}")
            yield f"event: done\ndata: {json.dumps({'session_id': thread_id, 'trace_id': trace_id}, ensure_ascii=False)}\n\n"

        except Exception as e:
            import traceback
            error_detail = traceback.format_exc()
            logger.error(f"[SSE] 异常: {str(e)}\n{error_detail}")
            yield f"event: error\ndata: {json.dumps({'code': 'SERVER_ERROR', 'message': f'服务异常: {str(e)}', 'trace_id': trace_id}, ensure_ascii=False)}\n\n"
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
