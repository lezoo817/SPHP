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
from app.infrastructure.cache.redis_client import get_and_delete_confirm_token_by_token
from app.orchestrator.graphs.main_graph import build_main_graph
from app.orchestrator.nodes.tool_executor import _execute_mcp
from app.orchestrator.state import AgentState

logger = logging.getLogger(__name__)

# 医疗安全声明（流式回复末尾补推，确保前端必见）
MEDICAL_DISCLAIMER = "\n\n---\n⚠️ **AI 建议仅供参考，不作为诊断依据。如有疑问请咨询专业医生。**"

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
    # 无 session 时先生成，保证 state / done / card 会话 ID 一致（L2 确认依赖）
    session_id = req.session_id or str(uuid4())
    initial_state = _build_initial_state(req, request, token, session_id)

    return StreamingResponse(
        _sse_generator(initial_state, session_id, trace_id),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "Connection": "keep-alive"},
    )


def _build_initial_state(
    req: ChatRequest, request: Request, token: str | None, session_id: str
) -> AgentState:
    """构造初始状态（不依赖外部 mutation）。"""
    return {
        "messages": [{"role": "user", "content": req.content}],
        "session_id": session_id,
        "intent": None,
        "user_id": getattr(request.state, "user_id", None),
        "scope": getattr(request.state, "scope", req.scope),
        "roles": None,
        "dept_id": None,
        "doctor_id": None,
        "hospital_id": (req.context or {}).get("hospital_id"),
        "tool_calls": None,
        "tool_results": None,
        "pending_confirmations": None,
        "risk_flags": [],
        "jwt_token": token,
        "rag_context": None,
        "tool_iteration": None,
    }


async def _sse_generator(
    initial_state: AgentState, session_id: str | None, trace_id: str
) -> AsyncIterator[str]:
    """SSE 流式输出生成器（系分 §6.2.1，基于 astream 混合 stream_mode）。

    ⚠️ 子图 custom 事件不传播：langgraph 1.2.9 中子图内 ``get_stream_writer()``
    推的自定义事件到不了主图 ``custom`` stream。而 tool_executor 运行在
    4 个业务子图内，因此 action/observation 事件从 ``updates`` 里的
    ``tool_calls``/``tool_results`` **重建**（子图最终 state 更新包含完整数据）。
    事件映射：
    - updates 含 tool_calls 字段      -> ``event: action``（工具开始，重建）
    - updates 含 tool_results 字段    -> ``event: observation``（工具结果，重建）
    - custom 且 type=tool_action      -> ``event: action``（预留顶层节点通道）
    - custom 且 type=tool_observation -> ``event: observation``（预留）
    - messages 且 msg 是 AIMessage    -> ``event: message``（回复 token）
    - updates 含 reply_node 输出      -> 未流式时兜底推送完整回复
    - 结束                            -> ``event: done``
    """
    graph = _get_graph()
    thread_id = session_id or str(uuid4())
    config = {"configurable": {"thread_id": thread_id}}

    logger.info("[SSE] 开始流程执行, thread_id=%s", thread_id)
    streamed_reply = False

    try:
        try:
            async for mode, chunk in graph.astream(
                initial_state,
                config=config,
                stream_mode=["custom", "messages", "updates"],
            ):
                if mode == "custom":
                    if not isinstance(chunk, dict):
                        continue
                    ctype = chunk.get("type", "")
                    if ctype == "tool_action":
                        yield _sse(
                            "action",
                            {"tool": chunk.get("tool", ""), "arguments": chunk.get("arguments", {})},
                        )
                    elif ctype == "tool_observation":
                        result = chunk.get("result", {})
                        obs = {
                            "tool": result.get("tool_name", ""),
                            "success": result.get("success", False),
                        }
                        if not result.get("success"):
                            error = result.get("error", {})
                            obs["error"] = error.get("message", "执行失败")
                        yield _sse("observation", obs)

                elif mode == "messages":
                    msg, meta = chunk
                    node = (meta or {}).get("langgraph_node", "")
                    # 仅 reply_node 的 assistant token 推送给前端
                    # （intent_node / tool_caller 的 LLM 输出不推送）
                    if node == "reply_node" and getattr(msg, "type", "") in ("ai", "AIMessageChunk"):
                        delta = getattr(msg, "content", "") or ""
                        if delta:
                            streamed_reply = True
                            yield _sse("message", {"delta": delta})

                elif mode == "updates":
                    # 子图更新里含 tool_calls / tool_results，重建 action / observation
                    for node_name, node_update in chunk.items():
                        if not isinstance(node_update, dict):
                            continue
                        tool_calls = node_update.get("tool_calls") or []
                        if tool_calls:
                            for tc in tool_calls:
                                yield _sse(
                                    "action",
                                    {
                                        "tool": tc.get("name", ""),
                                        "arguments": tc.get("arguments", {}),
                                    },
                                )
                        tool_results = node_update.get("tool_results") or []
                        if tool_results:
                            for tr in tool_results:
                                obs = {
                                    "tool": tr.get("tool_name", ""),
                                    "success": tr.get("success", False),
                                }
                                if not tr.get("success"):
                                    error = tr.get("error", {})
                                    obs["error"] = error.get("message", "执行失败")
                                yield _sse("observation", obs)

                        # L2 操作需用户确认：推送 card 事件（系分 §6.2.2）
                        pending_list = node_update.get("pending_confirmations") or []
                        for p in pending_list:
                            yield _sse("card", _build_card(p))

                    # reply_node 完成但未流式时，兜底推送完整回复
                    if "reply_node" in chunk and not streamed_reply:
                        output = chunk.get("reply_node", {})
                        content = _extract_last_content(output)
                        if content:
                            yield _sse("message", {"delta": content})

            logger.info("[SSE] 流程完成, session_id=%s", thread_id)
            # 流式回复后补推医疗安全声明（不在 LLM 流中，确保前端必见）
            if streamed_reply:
                yield _sse("message", {"delta": MEDICAL_DISCLAIMER})

        except GeneratorExit:
            logger.warning("[SSE] 客户端断开连接, session_id=%s", thread_id)
            raise
        except Exception as e:
            logger.error("[SSE] 异常: %s\n%s", e, traceback.format_exc())
            yield _sse(
                "error",
                {"code": "SERVER_ERROR", "message": "服务异常，请稍后重试", "trace_id": trace_id},
            )

    finally:
        # 确保 done 事件始终推送（即使异常/取消）
        yield _sse("done", {"session_id": thread_id, "trace_id": trace_id})


def _sse(event: str, payload: dict) -> str:
    """构造一条 SSE 事件。"""
    return f"event: {event}\ndata: {json.dumps(payload, ensure_ascii=False)}\n\n"


# 工具中文标签（系分 §6.2.2 card title/summary 展示）
_TOOL_LABELS = {
    "create_appointment": "确认挂号",
    "cancel_appointment": "确认取消挂号",
    "save_pre_consultation": "确认提交预问诊",
    "send_consultation_message": "确认发送问诊消息",
    "create_drug_order": "确认创建购药订单",
    "cancel_drug_order": "确认取消购药订单",
    "confirm_drug_receipt": "确认收货",
    "manage_allergy": "确认更新过敏史",
    "manage_medical_history": "确认更新既往史",
    "create_report": "确认录入检查报告",
    "update_medication_plan": "确认更新用药计划",
    "confirm_follow_up": "确认随访提醒",
    "join_waitlist": "确认登记候补",
    "generate_draft_note": "确认保存病历草稿",
}


def _build_card(pending: dict) -> dict:
    """构造 L2 确认卡片（系分 §6.2.2 card 事件）。"""
    tool_name = pending.get("tool_name", "")
    card_type = pending.get("card_type", "confirm_generic")
    title = _TOOL_LABELS.get(tool_name, "操作确认")
    # summary 取关键参数（slot_id/doctor_id/prescription_id 等）
    args = pending.get("tool_arguments", {})
    key_params = [v for v in args.values() if v is not None][:3]
    summary = f"{title}（参数: {', '.join(str(v) for v in key_params)}）" if key_params else title

    return {
        "card_type": card_type,
        "confirm_token": pending.get("confirm_token", ""),
        "session_id": pending.get("session_id", ""),
        "title": title,
        "summary": summary,
        "expires_at": pending.get("expires_at", ""),
    }


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
    Agent 原子消费 confirm_token（Redis 一次性 GET+DEL），校验 session/user，
    通过后同步执行对应的 MCP 工具，返回业务执行结果。

    Args:
        req: ConfirmRequest，含 confirm_token + session_id。
        request: FastAPI 请求，含 user_id（中间件注入）。

    Returns:
        ConfirmResponse: 统一信封，成功 data 含 action_result + message。
    """
    trace_id = getattr(request.state, "trace_id", "")
    user_id = str(getattr(request.state, "user_id", "") or "")

    # 校验参数非空
    if not req.confirm_token or not req.session_id:
        return ConfirmResponse(
            code="CONFIRM_INVALID", message="令牌格式无效", data=None, traceId=trace_id
        )

    # 原子消费 confirm_token（一次性 GET+DEL + user_id 校验）
    try:
        record = await get_and_delete_confirm_token_by_token(
            session_id=req.session_id,
            token_id=req.confirm_token,
            expected_user_id=user_id,
        )
    except Exception:
        # Redis 不可用
        logger.error("confirm_token 消费异常: Redis 不可用")
        return ConfirmResponse(
            code="CONFIRM_INVALID",
            message="操作暂时不可用，请稍后重试",
            data=None,
            traceId=trace_id,
        )

    if record is None:
        # token 不存在 / 已消费 / 已过期 / 用户不匹配
        return ConfirmResponse(
            code="CONFIRM_EXPIRED",
            message="操作已超时或无效，请重新发起",
            data=None,
            traceId=trace_id,
        )

    # session 校验（record 里的 session 应与请求一致）
    if record.get("session_id") != req.session_id:
        return ConfirmResponse(
            code="SESSION_MISMATCH", message="会话不匹配，请刷新重试", data=None, traceId=trace_id
        )

    # 执行对应的 L2 工具（匿名请求无 user_id，用 getattr 兜底）
    tool_name = record.get("tool_name", "")
    arguments = record.get("tool_arguments", {})
    state: AgentState = {
        "user_id": getattr(request.state, "user_id", None),
        "scope": getattr(request.state, "scope", "c_end"),
        "session_id": req.session_id,
    }
    result = await _execute_mcp(tool_name, arguments, state)

    if not result.get("success"):
        error = result.get("error", {})
        return ConfirmResponse(
            code="TOOL_FAILED",
            message=error.get("message", "操作执行失败"),
            data=None,
            traceId=trace_id,
        )

    # 成功：返回 action_result + message
    return ConfirmResponse(
        code="00000",
        message="操作成功",
        data={
            "action_result": result.get("data"),
            "message": _success_message(tool_name),
        },
        traceId=trace_id,
    )


def _success_message(tool_name: str) -> str:
    """L2 工具执行成功的面向用户提示（系分 §6.2.2 data.message）。"""
    messages = {
        "create_appointment": "挂号成功，请及时完成支付",
        "cancel_appointment": "挂号已取消",
        "save_pre_consultation": "预问诊已提交",
        "send_consultation_message": "消息已发送",
        "create_drug_order": "购药订单已创建",
        "cancel_drug_order": "购药订单已取消",
        "confirm_drug_receipt": "已确认收货",
        "manage_allergy": "过敏史已更新",
        "manage_medical_history": "既往史已更新",
        "create_report": "检查报告已录入",
        "update_medication_plan": "用药计划已更新",
        "confirm_follow_up": "随访提醒已确认",
        "join_waitlist": "已登记候补",
        "generate_draft_note": "病历草稿已保存",
    }
    return messages.get(tool_name, "操作成功")
