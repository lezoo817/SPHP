"""对话接口（系分 §6.2）。

前端直连 Agent 对话端点（SSE 流式）+ L2 确认回调。
"""

import json
import logging
import traceback
from collections.abc import AsyncIterator
from typing import Any, cast
from uuid import uuid4

from fastapi import APIRouter, Request
from fastapi.responses import JSONResponse, StreamingResponse

from app.api.schemas.chat import ChatRequest, ConfirmRequest, ConfirmResponse
from app.infrastructure.cache.redis_client import (
    get_and_delete_confirm_done,
    get_and_delete_confirm_token_by_token,
    set_confirm_done,
)
from app.orchestrator.graphs.main_graph import build_main_graph
from app.orchestrator.nodes.reply import MEDICAL_DISCLAIMER
from app.orchestrator.nodes.tool_executor import _execute_mcp
from app.orchestrator.state import AgentState

logger = logging.getLogger(__name__)

router = APIRouter()

# 全局主图实例（编译后的 CompiledGraph）
_main_graph = None


def _get_graph() -> Any:
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
    # M5-T4（T-M3-L1）：读取本会话此前确认成功的操作回执（一次性消费），
    # 注入对话上下文，使"确认后追问"保持连续。
    # Redis 故障时优雅降级：回执读取失败返回 []，不影响对话主链路
    # （与限流 fail-open、L2 转 risk_flags 的降级口径一致）
    try:
        confirmed_actions = await get_and_delete_confirm_done(session_id)
    except Exception:
        logger.warning("读取 confirm_done 回执失败（Redis 不可用），降级为空列表")
        confirmed_actions = []
    initial_state = _build_initial_state(req, request, token, session_id, confirmed_actions)

    return StreamingResponse(
        _sse_generator(initial_state, session_id, trace_id),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "Connection": "keep-alive"},
    )


def _build_initial_state(
    req: ChatRequest,
    request: Request,
    token: str | None,
    session_id: str,
    confirmed_actions: list[dict[str, Any]] | None = None,
) -> AgentState:
    """构造初始状态（不依赖外部 mutation）。

    Args:
        req: 前端对话请求。
        request: FastAPI 请求（含中间件注入的 user_id / scope / roles 等身份）。
        token: JWT Token。
        session_id: 会话 ID。
        confirmed_actions: M5-T4（T-M3-L1）本会话此前确认成功的操作回执，
            非空时注入 messages 上下文，保证确认后追问连续。

    Note:
        M6-B1 鉴权去重：中间件已在 HTTP 层调 Java token/parse 一次，身份字段
        （user_id / roles / dept_id / doctor_id / hospital_id）在此完整复制进
        AgentState，auth_node 不再重复调 Java。
    """
    messages: list[dict[str, Any]] = [{"role": "user", "content": req.content}]
    if confirmed_actions:
        labels = [
            _TOOL_LABELS.get(t.get("tool_name", ""), t.get("tool_name", "操作"))
            for t in confirmed_actions
        ]
        messages.insert(
            0,
            {
                "role": "system",
                "content": "本会话此前用户已确认完成以下操作："
                + "；".join(labels)
                + "。后续用户提及这些操作时，基于已执行结果回答，不要重复要求确认。",
            },
        )

    return {
        "messages": messages,
        "session_id": session_id,
        "intent": None,
        "user_id": getattr(request.state, "user_id", None),
        "scope": getattr(request.state, "scope", req.scope),
        # M6-B1 鉴权去重：完整复制中间件注入的 B 端身份字段，auth_node 直接消费
        "roles": getattr(request.state, "roles", None),
        "dept_id": getattr(request.state, "dept_id", None),
        "doctor_id": getattr(request.state, "doctor_id", None),
        # C 端 hospital_id 无中间件注入值（None）时退回请求 context
        "hospital_id": getattr(request.state, "hospital_id", None)
        or (req.context or {}).get("hospital_id"),
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
    事件映射（M6-A1 定案：仅 updates 重建，custom 通道已移除）：
    - updates 含 tool_results 字段    -> ``event: action`` + ``event: observation``
                                        （工具已执行，逐结果配对推送）
    - updates 含 tool_calls 字段      -> ``event: action``（仅 tool_calls 无结果场景）
    - messages 且 msg 是 AIMessage    -> ``event: message``（回复 token）
    - messages 含 reasoning_content   -> ``event: thought``（推理思考 token，M6-B4）
    - updates 含 reply_node 输出      -> 未流式时兜底推送完整回复
    - 结束                            -> ``event: done``（含 usage token 统计，M6-B4）
    """
    graph = _get_graph()
    thread_id = session_id or str(uuid4())
    config = {"configurable": {"thread_id": thread_id}}

    logger.info("[SSE] 开始流程执行, thread_id=%s", thread_id)
    streamed_reply = False
    # 本轮 LLM token 用量（M6-B4 done.usage）：langgraph 首个 chunk 含
    # input_tokens，后续 chunk 只递增 output_tokens
    usage: dict[str, int] = {"prompt_tokens": 0, "completion_tokens": 0, "total_tokens": 0}

    try:
        try:
            async for mode, chunk in graph.astream(
                initial_state,
                config=config,
                stream_mode=["messages", "updates"],
            ):
                if mode == "messages":
                    msg, meta = chunk
                    node = (meta or {}).get("langgraph_node", "")
                    # 累积 LLM 用量（M6-B4）：对全部 LLM chunk 统计
                    usage_meta = getattr(msg, "usage_metadata", None) or {}
                    if usage_meta.get("input_tokens"):
                        usage["prompt_tokens"] = usage_meta["input_tokens"]
                    if usage_meta.get("output_tokens"):
                        usage["completion_tokens"] += usage_meta["output_tokens"]
                    if usage_meta.get("total_tokens"):
                        usage["total_tokens"] = usage_meta["total_tokens"]
                    # 仅 reply_node 的 assistant token 推送给前端
                    # （intent_node / tool_caller 的 LLM 输出不推送）
                    if node == "reply_node" and getattr(msg, "type", "") in (
                        "ai",
                        "AIMessageChunk",
                    ):
                        delta = getattr(msg, "content", "") or ""
                        if delta:
                            streamed_reply = True
                            yield _sse("message", {"delta": delta})
                        # 推理模型思考 token（系分 §5.12，M6-B4）：
                        # 智谱 glm-4 系列流式输出在 additional_kwargs.reasoning_content
                        reasoning = (getattr(msg, "additional_kwargs", {}) or {}).get(
                            "reasoning_content"
                        )
                        if reasoning:
                            yield _sse("thought", {"delta": reasoning})

                elif mode == "updates":
                    # 子图更新里含 tool_calls / tool_results，重建 action / observation
                    for node_name, node_update in chunk.items():
                        if not isinstance(node_update, dict):
                            continue
                        tool_calls = node_update.get("tool_calls") or []
                        tool_results = node_update.get("tool_results") or []
                        if tool_results:
                            # 子图循环会把 tool_calls 覆盖为空，action 无法从 tool_calls
                            # 重建，改为从 tool_results 反推（工具确已执行）：
                            # 每个结果推送 action -> observation 配对
                            for tr in tool_results:
                                yield _sse("action", _build_action(tr))
                                yield _sse("observation", _build_observation(tr))
                        elif tool_calls:
                            # 仅 tool_calls 无结果（理论顶层场景）：只推 action
                            for tc in tool_calls:
                                yield _sse("action", _build_action(tc, from_call=True))

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
            # P1-3 客户端断开：记录后直接结束，不补推 done（无人接收）。
            # 必须 return 而非 raise + finally 中 yield —— 生成器关闭（GeneratorExit
            # 传播）期间在 finally 里 yield 会抛 "generator ignored GeneratorExit"
            # RuntimeError，生产必现（客户端断连刷日志/连接清理异常）。
            logger.warning("[SSE] 客户端断开连接, session_id=%s", thread_id)
            return
        except Exception as e:
            logger.error("[SSE] 异常: %s\n%s", e, traceback.format_exc())
            yield _sse(
                "error",
                {"code": "SERVER_ERROR", "message": "服务异常，请稍后重试", "trace_id": trace_id},
            )

        # P1-3：done 事件移至正常/异常兜底路径（非 finally），生成器迭代期
        # yield 是安全的；M6-B4：携带本轮 LLM token 用量（无任何 LLM chunk 时
        # usage 为 None）。客户端断开时提前 return，不执行到此处。
        _usage = {k: v for k, v in usage.items() if v} or None
        yield _sse(
            "done",
            {"session_id": thread_id, "trace_id": trace_id, "usage": _usage},
        )

    finally:
        # 清理段：仅日志，不 yield。正常结束 / 异常兜底 / 客户端断开（return）
        # 三条路径都安全收敛，不会在生成器关闭期间再次 yield。
        logger.info("[SSE] 生成器退出, session_id=%s", thread_id)


def _sse(event: str, payload: dict[str, Any]) -> str:
    """构造一条 SSE 事件。"""
    return f"event: {event}\ndata: {json.dumps(payload, ensure_ascii=False)}\n\n"


def _build_action(result: dict[str, Any], *, from_call: bool = False) -> dict[str, Any]:
    """构建 action 事件（系分 §5.12 / §6.2.1：{tool, label, arguments}）。

    Args:
        result: tool_results 项（键 tool_name）或 tool_calls 项（键 name）。
        from_call: True 表示输入来自 tool_calls 项（用 name 读取工具名）。

    Returns:
        dict: action 事件负载，label 为工具中文标签（无映射时回退工具名）。
    """
    tool = result.get("name" if from_call else "tool_name", "")
    return {
        "tool": tool,
        "label": _TOOL_LABELS.get(tool, tool),
        "arguments": result.get("arguments", {}),
    }


def _build_observation(result: dict[str, Any]) -> dict[str, Any]:
    """构建 observation 事件（系分 §5.12 / §6.2.1：{tool, status, result, summary, duration_ms}）。

    从 tool_executor 的 tool_results 项重建：
        - status: success / error（由 success 布尔映射）
        - result: 成功时解 Java 信封后的内层业务数据（如 {"departments": [...]}），失败为 None
        - summary: 一行摘要（成功统计条数 / 失败取错误消息）
        - duration_ms: 工具执行耗时（tool_executor 记录，未记录时 0）
    """
    success = result.get("success", False)
    data = result.get("data")
    error = result.get("error", {})
    return {
        "tool": result.get("tool_name", ""),
        "status": "success" if success else "error",
        "result": _unwrap_envelope(data) if success else None,
        "summary": _build_observation_summary(success, data, error),
        "duration_ms": round(result.get("duration_ms", 0)),
    }


def _unwrap_envelope(data: Any) -> Any:
    """解 Java 统一信封（{code, message, data, traceId}）取内层业务数据。

    仅命中成功信封（code=00000 且有 data 键）时解一层；非信封格式
    （本地工具结果）或业务失败码信封原样返回。与编排层
    ``reply._format_tool_results`` 的解包呼应：消费层与 SSE 展示层各自解包。
    """
    if isinstance(data, dict) and data.get("code") == "00000" and "data" in data:
        return data["data"]
    return data


def _build_observation_summary(success: bool, data: Any, error: dict[str, Any]) -> str:
    """生成 observation.summary 一行摘要（前端折叠态展示）。

    成功：统计返回数据中的列表条数（如"返回 3 条数据"），无列表时取 JSON 前 100 字符；
    失败：取错误消息。
    """
    if not success:
        return str(error.get("message", "执行失败"))
    count = _first_list_count(data, depth=0)
    if count is not None:
        return f"返回 {count} 条数据"
    text = json.dumps(data, ensure_ascii=False) if data is not None else "无返回数据"
    return text[:100] + ("…" if len(text) > 100 else "")


def _first_list_count(data: Any, depth: int) -> int | None:
    """深度优先查找 data 内第一个 list 的长度（可命中 Java 信封内层列表）。"""
    if isinstance(data, list):
        return len(data)
    if isinstance(data, dict) and depth < 2:
        for v in data.values():
            count = _first_list_count(v, depth + 1)
            if count is not None:
                return count
    return None


# 工具中文标签（P1 契约对齐：action.label / card.title 展示，覆盖全部 39 个工具）
_TOOL_LABELS = {
    # ---- C 端（30）----
    "create_triage_assessment": "导诊评估",
    "search_medical_knowledge": "检索医疗知识",
    "query_departments": "查询科室",
    "query_doctors": "查询医生",
    "query_schedule_slots": "查询号源时段",
    "query_appointments": "查询挂号订单",
    "query_payment_status": "查询支付状态",
    "query_consultations": "查询问诊记录",
    "query_prescriptions": "查询处方",
    "interpret_prescription": "解读处方",
    "query_pharmacy_stock": "查询药店库存",
    "query_drug_orders": "查询购药订单",
    "query_health_record": "查询健康档案",
    "query_reports": "查询检查报告",
    "query_medication_plans": "查询用药计划",
    "query_follow_ups": "查询随访计划",
    "manage_notifications": "管理通知",
    # ---- L2 确认类（card 标题，系分 §6.2.2）----
    "create_appointment": "确认挂号",
    "cancel_appointment": "确认取消挂号",
    "save_pre_consultation": "确认提交预问诊",
    "send_consultation_message": "确认发送问诊消息",
    "create_drug_order": "确认创建购药订单",
    "cancel_drug_order": "确认取消购药订单",
    "confirm_drug_receipt": "确认收货",
    "join_waitlist": "确认登记候补",
    "manage_allergy": "确认更新过敏史",
    "manage_medical_history": "确认更新既往史",
    "create_report": "确认录入检查报告",
    "update_medication_plan": "确认更新用药计划",
    "confirm_follow_up": "确认随访提醒",
    "generate_draft_note": "确认保存病历草稿",
    "query_patient_history": "确认查询患者档案",
    # ---- B 端（9）----
    "query_drug_guide": "查询药品说明书",
    "check_drug_interaction": "查询药品相互作用",
    "check_contraindication": "查询药品禁忌",
    "check_allergy_risk": "查询过敏风险",
    "check_duplicate_medication": "查询重复用药",
    "recommend_care": "查询号源推荐",
    "interpret_report": "解读检查报告",
}


def _build_card(pending: dict[str, Any]) -> dict[str, Any]:
    """构造 L2 确认卡片（系分 §6.2.2 card 事件，P1 契约对齐补 details）。

    details 按 card_type 从 tool_arguments 提取确认前可得的字段（ID/主诉/动作等）。
    名称类字段（department_name / doctor_name 等）依赖 L2 执行后回填，确认前不可得，
    故不在此伪造；字段缺失时前端按 card_type 降级展示。
    """
    tool_name = pending.get("tool_name", "")
    card_type = pending.get("card_type", "confirm_generic")
    title = _TOOL_LABELS.get(tool_name, "操作确认")
    # summary 取关键参数（slot_id/doctor_id/prescription_id 等）
    args = pending.get("tool_arguments", {})
    key_params = [v for v in args.values() if v is not None][:3]
    summary = f"{title}（参数: {', '.join(str(v) for v in key_params)}）" if key_params else title
    details = {k: args[k] for k in _CARD_DETAILS_FIELDS.get(card_type, ()) if k in args}

    return {
        "card_type": card_type,
        "confirm_token": pending.get("confirm_token", ""),
        "session_id": pending.get("session_id", ""),
        "title": title,
        "summary": summary,
        "details": details,
        "expires_at": pending.get("expires_at", ""),
    }


# card.details 字段映射（系分 §6.2.1）：从 tool_arguments 提取确认前可得的字段。
# 名称类字段（department_name/doctor_name 等）依赖执行后回填，确认前仅透出 ID 类参数。
_CARD_DETAILS_FIELDS: dict[str, tuple[str, ...]] = {
    "confirm_appointment": ("slot_id", "hospital_id", "patient_id"),
    "confirm_cancel_appointment": ("appointment_id",),
    "confirm_pre_consultation": ("appointment_id", "chief_complaint"),
    "confirm_send_message": ("consultation_id", "content"),
    "confirm_drug_order": ("prescription_id", "pharmacy_id", "delivery_address"),
    "confirm_cancel_drug_order": ("drug_order_id",),
    "confirm_drug_receipt": ("drug_order_id",),
    "confirm_waitlist": ("slot_id", "patient_id"),
    "confirm_allergy": ("allergen", "reaction", "allergy_id"),
    "confirm_medical_history": ("content", "history_id"),
    "confirm_report": ("report_name", "report_date"),
    "confirm_medication_plan": ("plan_id", "action"),
    "confirm_follow_up": ("follow_up_id", "remind_at"),
    "confirm_draft_note": ("consultation_id",),
    "confirm_patient_history": ("patient_id",),
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
        # content 可能为 None / 非字符串，统一转 str
        return str(last.get("content", ""))
    return getattr(last, "content", "")


@router.post("/chat/confirm", response_model=ConfirmResponse)
async def chat_confirm(req: ConfirmRequest, request: Request) -> ConfirmResponse | JSONResponse:
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
        return _confirm_error("CONFIRM_INVALID", "令牌格式无效", trace_id)

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
        return _confirm_error("CONFIRM_INVALID", "操作暂时不可用，请稍后重试", trace_id)

    if record is None:
        # token 不存在 / 已消费 / 已过期 / 用户不匹配
        return _confirm_error("CONFIRM_EXPIRED", "操作已超时或无效，请重新发起", trace_id)

    # session 校验（record 里的 session 应与请求一致）
    if record.get("session_id") != req.session_id:
        return _confirm_error("SESSION_MISMATCH", "会话不匹配，请刷新重试", trace_id)

    # 执行对应的 L2 工具（匿名请求无 user_id，用 getattr 兜底）
    tool_name = record.get("tool_name", "")
    arguments = record.get("tool_arguments", {})
    # _execute_mcp 只消费 user_id/scope/session_id，无需完整 AgentState，
    # cast 表明这是按需构造的部分状态
    state = cast(
        AgentState,
        {
            "user_id": getattr(request.state, "user_id", None),
            "scope": getattr(request.state, "scope", "c_end"),
            "session_id": req.session_id,
        },
    )
    result = await _execute_mcp(tool_name, arguments, state)

    if not result.get("success"):
        error = result.get("error", {})
        # TOOL_FAILED 系分 §6.1 错误码表标注为 500
        return _confirm_error(
            "TOOL_FAILED", error.get("message", "操作执行失败"), trace_id, status_code=500
        )

    # M5-T4（T-M3-L1）：写入确认成功回执，供下一轮对话引用（此操作是独立
    # HTTP 请求，结果不进 graph 状态；Redis 回执 + 下轮注入保证对话连续）
    try:
        await set_confirm_done(req.session_id, tool_name, result.get("data"))
    except Exception:
        logger.warning("写入 confirm_done 回执失败: tool=%s", tool_name)

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


def _confirm_error(code: str, message: str, trace_id: str, status_code: int = 400) -> JSONResponse:
    """构造 L2 确认回调错误响应（系分 §6.2.2 错误码表）。

    错误路径返回对应 HTTP 状态码（CONFIRM_*/SESSION_MISMATCH=400，
    TOOL_FAILED=500）+ 统一信封 {code, message, data, traceId}，
    与 §6.1 信封契约一致。

    Args:
        code: 错误码（CONFIRM_INVALID / CONFIRM_EXPIRED / SESSION_MISMATCH / TOOL_FAILED）。
        message: 用户可读错误提示。
        trace_id: 链路追踪号。
        status_code: HTTP 状态码，默认 400；工具执行失败传 500。

    Returns:
        JSONResponse: 对应状态码 + 统一信封。
    """
    return JSONResponse(
        status_code=status_code,
        content={"code": code, "message": message, "data": None, "traceId": trace_id},
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
        "query_patient_history": "患者档案已查询",
    }
    return messages.get(tool_name, "操作成功")
