"""对话接口（系分 §6.2）。

前端直连 Agent 对话端点（SSE 流式）+ L2 确认回调。
"""

import asyncio
import json
import logging
import traceback
from collections.abc import AsyncIterator
from typing import Any, cast
from uuid import uuid4

from fastapi import APIRouter, Request
from fastapi.responses import JSONResponse, StreamingResponse

from app.api.schemas.chat import ChatRequest, ConfirmRequest, ConfirmResponse
from app.api.schemas.envelope import error_response, success_response
from app.infrastructure.cache.redis_client import (
    delete_confirm_token_by_token,
    get_and_delete_confirm_done,
    get_confirm_token_by_token,
    set_confirm_done,
)
from app.orchestrator.graphs.main_graph import build_main_graph
from app.orchestrator.nodes.tool_executor import execute_mcp_tool
from app.orchestrator.session_store import get_session_store
from app.orchestrator.state import AgentState

logger = logging.getLogger(__name__)

router = APIRouter()

# 全局主图实例（编译后的 CompiledGraph）
_main_graph = None

# P1-9 会话级并发锁：同 session 的并发对话请求串行化。
# 主图 checkpointer 锁 per-instance（memory: MemorySaver 线程锁 / postgres:
# 表级读写），无法跨请求协调同 thread_id 的并发。两个并发请求 astream 同一个
# thread_id 会交错读写同一 checkpoint，后写覆盖先写，state 互相污染。
# 此处按 session_id 维护 asyncio.Lock，先到者持有锁、后者排队，从接入层
# 保证同会话串行；不同会话互不影响。
#
# 引用计数防字典无限增长：refcount 在请求进入临界区前 +1、释放会话锁后 -1。
# 计数非 0（含排队等待锁的请求）时不移除条目，避免"排队中的 B 被移除锁后，
# 新请求 C 新建锁而不等待 B"破坏串行性。
_session_locks: dict[str, asyncio.Lock] = {}
_session_refcounts: dict[str, int] = {}
_session_lock_guard = asyncio.Lock()


async def _get_session_lock(session_id: str) -> asyncio.Lock:
    """获取指定会话的串行化锁（不存在则创建，P1-9）。

    Args:
        session_id: 会话 ID，同会话共享同一把锁。

    Returns:
        asyncio.Lock: 会话级锁，调用方需 ``async with`` 包裹图执行，
            并在结束后调用 ``_release_session_lock`` 归还引用。
    """
    async with _session_lock_guard:
        lock = _session_locks.get(session_id)
        if lock is None:
            lock = _session_locks[session_id] = asyncio.Lock()
        _session_refcounts[session_id] = _session_refcounts.get(session_id, 0) + 1
        return lock


async def _release_session_lock(session_id: str) -> None:
    """归还会话锁引用，引用归零时移除条目（防字典无限增长，P1-9）。"""
    async with _session_lock_guard:
        remaining = _session_refcounts.get(session_id, 0) - 1
        if remaining <= 0:
            _session_locks.pop(session_id, None)
            _session_refcounts.pop(session_id, None)
        else:
            _session_refcounts[session_id] = remaining


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
    # P2 安全：按 user_id 归属过滤（匿名传空串），防止跨用户读回执（含 PHI）。
    request_user_id = str(getattr(request.state, "user_id", "") or "")
    try:
        confirmed_actions = await get_and_delete_confirm_done(session_id, request_user_id)
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
            非空时注入 messages 上下文（含操作结果摘要），保证确认后追问连续。

    Note:
        M6-B1 鉴权去重：中间件已在 HTTP 层调 Java token/parse 一次，身份字段
        （user_id / roles / dept_id / doctor_id / hospital_id）在此完整复制进
        AgentState，auth_node 不再重复调 Java。
    """
    messages: list[dict[str, Any]] = [{"role": "user", "content": req.content}]
    if confirmed_actions:
        # P2：注入操作结果摘要（操作名 + 成功提示 + 关键业务 ID），
        # 支持"我的挂号单号是多少"式追问，而非仅有操作名标签
        messages.insert(
            0,
            {
                "role": "system",
                "content": _build_confirmed_actions_system(confirmed_actions),
            },
        )

    scope = getattr(request.state, "scope", req.scope)
    return {
        "messages": messages,
        "session_id": session_id,
        # intent 不在此初始化：意图粘性依赖 checkpointer 跨轮保留 state.intent，
        # 此处置 None 会覆盖历史意图，第二轮 intent_node 读到 None 走非粘性分支，
        # 问诊中补症状被重判 triage。首轮无历史时 state.get("intent") 返回 None，
        # intent_node 正常走非粘性分类。
        "user_id": getattr(request.state, "user_id", None),
        "scope": scope,
        # M6-B1 鉴权去重：完整复制中间件注入的 B 端身份字段，auth_node 直接消费
        "roles": getattr(request.state, "roles", None),
        "dept_id": getattr(request.state, "dept_id", None),
        "doctor_id": getattr(request.state, "doctor_id", None),
        # 按 scope 解析 hospital_id：C 端 context（页面当前选择的医院）优先、
        # JWT 兜底；B 端 JWT（医生所属医院）权威、context 不覆盖（防跨医院越权）。
        # 原 `or` 逻辑 JWT 恒优先，C 端 JWT 带 hospitalId 时会忽略页面切换的医院。
        "hospital_id": _resolve_hospital_id(scope, request, req.context),
        # M8-5：当前问诊患者 ID 只来自请求 context（B 端医生接诊时前端选中，
        # C 端就诊人切换可选），JWT 鉴权无此字段，故不做中间件回退。
        # 供 tool_caller 注入 LLM 上下文并对必填 patient_id 工具确定性补全。
        "patient_id": (req.context or {}).get("patient_id"),
        # 对齐原始需求 §3：用户收货地址 ID 只来自请求 context（前端页面选中的配送地址），
        # 供 recommend_pharmacies 工具确定性补全与 LLM 上下文注入。
        "address_id": (req.context or {}).get("address_id"),
        "tool_calls": None,
        "tool_results": None,
        "pending_confirmations": None,
        "risk_flags": [],
        "jwt_token": token,
        "rag_context": None,
        "tool_iteration": None,
    }


def _resolve_hospital_id(
    scope: str, request: Request, context: dict[str, Any] | None
) -> int | None:
    """按 scope 解析 hospital_id 优先级（系分 §6.2 context 表）。

    C 端：hospital_id 是页面"当前选择的医院"（查科室/号源等工具必填），
    前端每次切换医院经 context 传入，故 **context 优先、JWT 兜底**——若 JWT
    携带的默认 hospitalId 覆盖 context，用户切医院后挂号/导诊会打到旧医院。

    B 端：医生所属医院以 JWT（token/parse 的 hospitalId）为准，**context 不
    覆盖**——医生必须且只能在自己所属医院内操作，防前端伪造跨医院越权。

    Args:
        scope: 当前服务端 c_end / b_end。
        request: FastAPI 请求（含中间件注入的 hospital_id）。
        context: 请求附加上下文（含 context.hospital_id）。

    Returns:
        int | None: 解析后的医院 ID；两者均缺失时返回 None。
    """
    jwt_hospital_id = getattr(request.state, "hospital_id", None)
    ctx_hospital_id = (context or {}).get("hospital_id")
    if scope == "b_end":
        return jwt_hospital_id
    return ctx_hospital_id if ctx_hospital_id is not None else jwt_hospital_id


def _handle_message_chunk(
    msg: Any,
    meta: dict[str, Any] | None,
    usage: dict[str, int],
) -> tuple[list[tuple[str, dict[str, Any]]], bool]:
    """messages 分支处理：累积 LLM 用量，返回（事件列表, 是否推送回复文本）。

    ⚠️ 仅 reply_node 的 assistant token 推送给前端（intent_node / tool_caller
    的 LLM 输出不推送）；推理模型思考 token（reasoning_content）单独作为
    ``thought`` 事件推送（系分 §5.12，M6-B4）。``thought`` / ``error`` /
    ``done.trace_id`` 是对系分 §6.2.1 事件表的**扩展**（M6-B4 / P1-3 定案），
    前端按未知事件忽略处理。

    Args:
        msg: langgraph messages 模式下的消息对象（BaseMessage 或其 chunk）。
        meta: 消息元数据（含 langgraph_node 节点名）。
        usage: 本轮 LLM token 用量累计表（就地更新，M6-B4 done.usage）。

    Returns:
        tuple[list[tuple[str, dict[str, Any]]], bool]：
            (事件列表, 是否推送了回复文本)；事件为 (event, payload) 二元组，
            event ∈ {"message", "thought"}。
    """
    events: list[tuple[str, dict[str, Any]]] = []
    streamed = False
    node = (meta or {}).get("langgraph_node", "")
    # 累积 LLM 用量（M6-B4）：对全部 LLM chunk 统计。
    # P3-6：is not None 判断（0 为合法计数，勿用 if x: 误判）。
    usage_meta = getattr(msg, "usage_metadata", None) or {}
    if usage_meta.get("input_tokens") is not None:
        usage["prompt_tokens"] = usage_meta["input_tokens"]
    if usage_meta.get("output_tokens") is not None:
        usage["completion_tokens"] += usage_meta["output_tokens"]
    if usage_meta.get("total_tokens") is not None:
        usage["total_tokens"] = usage_meta["total_tokens"]
    # 仅 reply_node 的 assistant token 推送给前端（intent_node / tool_caller 不推送）
    if node == "reply_node" and getattr(msg, "type", "") in ("ai", "AIMessageChunk"):
        delta = getattr(msg, "content", "") or ""
        if delta:
            streamed = True
            events.append(("message", {"delta": delta}))
        # 推理模型思考 token（系分 §5.12，M6-B4）：智谱 glm-4 系列流式输出在
        # additional_kwargs.reasoning_content
        reasoning = (getattr(msg, "additional_kwargs", {}) or {}).get("reasoning_content")
        if reasoning:
            events.append(("thought", {"delta": reasoning}))
    return events, streamed


def _call_signature(kind: str, name: str, arguments: dict[str, Any] | None) -> tuple[str, str, str]:
    """构造工具调用签名（去重键）。

    kind 区分 action / observation / card，使 action 与其配对的 observation
    互不挡（同一工具同一参数下，action 与 observation 各推一次）；arguments
    序列化保证同工具不同参数均推送、同工具同参数二次出现才去重。

    Args:
        kind: 事件类别（action / observation / card）。
        name: 工具名（card 用 confirm_token 或 tool_name）。
        arguments: 工具参数（card 用 tool_arguments），序列化为去重键组成部分。

    Returns:
        tuple[str, str, str]: 可哈希签名，入 seen 集合去重。
    """
    return (
        kind,
        name,
        json.dumps(arguments or {}, sort_keys=True, ensure_ascii=False),
    )


def _handle_updates_chunk(
    chunk: dict[str, Any], seen: set[tuple[str, str, str]] | None = None
) -> list[tuple[str, dict[str, Any]]]:
    """updates 分支处理：从子图更新重建 action / observation / card / options 事件。

    subgraphs=True（方案 B 实时上报）：子图内部节点（tool_caller /
    safety_check / tool_executor）的 updates 逐节点实时上浮到主图流，每轮
    工具完成即推 action/observation、L2 即推 card、问诊选医生即推 options。
    因 tool_results / pending_confirmations / pending_doctor_choices 自累积
    + 子图结束后主图层级再上报整体更新，同一结果会被重复上报，需 seen 签名去重。

    去重粒度（kind 区分，配对事件不互相挡）：
        - action：``("action", tool_name, arguments)``--tool_caller 已推过
          from_call 则 tool_executor 不重复推 action（仅补 observation）
        - observation：``("observation", tool_name, arguments)``--自累积
          重复结果只推一次
        - card：``("card", confirm_token or tool_name, arguments)``--
          子图与主图层级重复上报只推一次
        - options：``("options", options_id, "")``--按 options_id 去重，
          选医生候选只在首次写入时推一次

    seen 为 None 时内部新建空集，保持单参调用兼容（现有单测无需改）。

    Args:
        chunk: updates 模式下的一帧（node_name -> state 更新 dict）。
        seen: 跨帧去重签名集（_sse_generator 传入，跨子图层级累积）。

    Returns:
        list[tuple[str, dict[str, Any]]]：(event, payload) 事件列表，
            event ∈ {"action", "observation", "card", "options"}。
    """
    if seen is None:
        seen = set()
    events: list[tuple[str, dict[str, Any]]] = []
    for _node_name, node_update in chunk.items():
        if not isinstance(node_update, dict):
            continue
        tool_calls = node_update.get("tool_calls") or []
        tool_results = node_update.get("tool_results") or []
        if tool_results:
            for tr in tool_results:
                tool = tr.get("tool_name", "")
                args = tr.get("arguments", {})
                # observation 签名去重：自累积重复结果只推一次
                obs_sig = _call_signature("observation", tool, args)
                if obs_sig in seen:
                    continue
                # action 签名去重：tool_caller 已推 from_call 则不重复推
                action_sig = _call_signature("action", tool, args)
                if action_sig not in seen:
                    seen.add(action_sig)
                    events.append(("action", _build_action(tr)))
                seen.add(obs_sig)
                events.append(("observation", _build_observation(tr)))
        elif tool_calls:
            # 仅 tool_calls 无结果（工具开始执行）：action 签名去重后推送
            for tc in tool_calls:
                tool = tc.get("name", "")
                args = tc.get("arguments", {})
                action_sig = _call_signature("action", tool, args)
                if action_sig in seen:
                    continue
                seen.add(action_sig)
                events.append(("action", _build_action(tc, from_call=True)))
        # L2 操作需用户确认：card 按 confirm_token 去重（系分 §6.2.2）
        for p in node_update.get("pending_confirmations") or []:
            card_key = p.get("confirm_token") or p.get("tool_name", "")
            card_sig = _call_signature("card", card_key, p.get("tool_arguments", {}))
            if card_sig in seen:
                continue
            seen.add(card_sig)
            events.append(("card", _build_card(p)))
        # 问诊选医生候选：options 按 options_id 去重（M8-6）
        choices = node_update.get("pending_doctor_choices")
        if choices:
            options_id = f"opt_{_hash_choices(choices)}"
            opt_sig = _call_signature("options", options_id, {})
            if opt_sig not in seen:
                seen.add(opt_sig)
                events.append(("options", _build_options(choices, options_id)))
    return events


def _hash_choices(choices: list[dict[str, Any]]) -> str:
    """候选医生列表生成稳定哈希（options_id 后缀，去重键）。

    用 doctor_id 列表拼接哈希，保证同候选只推一次 options；候选变化（换医生）
    则生成不同 options_id 触发新卡。
    """
    import hashlib as _hashlib

    key = ",".join(str(c.get("doctor_id", "")) for c in choices)
    return _hashlib.md5(key.encode()).hexdigest()[:8]


def _build_options(choices: list[dict[str, Any]], options_id: str) -> dict[str, Any]:
    """构造 options 事件（M8-6 问诊选医生卡片）。

    候选医生（来自 query_doctors 经 _parse_doctor_candidates 解析）映射为前端
    select_doctor 卡片所需 items。id 用 doctor_id（稳定键，与用户回传匹配），
    label 用医生姓名，description 含科室与职称，meta 含擅长领域与挂号费（如有）。

    字段契约与前端 typings/agent.ts 的 AgentOptionsEvent / AgentSelectItem 对齐：
    type / items(id,label,description,meta) / prompt / reply_template。
    options_id 仅后端去重用，前端不消费。

    Args:
        choices: pending_doctor_choices 候选医生列表。
        options_id: 选项组标识（去重键）。

    Returns:
        dict: options 事件负载，与 card 事件平级、由前端单独渲染。
    """
    items: list[dict[str, Any]] = []
    for c in choices:
        desc_parts = []
        if c.get("dept_name"):
            desc_parts.append(c["dept_name"])
        if c.get("title"):
            desc_parts.append(c["title"])
        meta: dict[str, Any] = {}
        if c.get("specialty"):
            meta["specialty"] = c["specialty"]
        if c.get("fee_cent") is not None:
            meta["fee_cent"] = c["fee_cent"]
        items.append(
            {
                "id": str(c.get("doctor_id", "")),
                "label": c.get("name", ""),
                "description": " · ".join(desc_parts) if desc_parts else "",
                "meta": meta,
            }
        )
    return {
        "type": "select_doctor",
        "options_id": options_id,
        "prompt": "请选择您想咨询的医生",
        "reply_template": "我选择{label}",
        "items": items,
    }


async def _sse_generator(
    initial_state: AgentState, session_id: str | None, trace_id: str
) -> AsyncIterator[str]:
    """SSE 流式输出生成器（系分 §6.2.1，基于 astream 混合 stream_mode）。

    事件映射（方案 B 实时上报：astream subgraphs=True + seen 去重）：
    - updates（含子图内部节点）含 tool_results -> ``event: action`` + ``event: observation``
      （action 经 seen 去重，tool_caller 已推则 tool_executor 仅补 observation）
    - updates 含 tool_calls（无结果场景）-> ``event: action``（工具开始执行）
    - messages 且 msg 是 AIMessage    -> ``event: message``（回复 token）
    - messages 含 reasoning_content   -> ``event: thought``（推理思考 token，M6-B4）
    - updates 含 reply_node 输出      -> 未流式时兜底推送完整回复
    - updates 含 pending_confirmations -> ``event: card``（按 confirm_token 去重）
    - 结束                            -> ``event: done``（含 usage token 统计，M6-B4）

    subgraphs=True 展开子图内部节点 updates 实时上浮（langgraph 1.2.9），
    输出三元组 ``(namespace, mode, chunk)``；namespace 不消费（解包丢弃）。
    messages 流中子图内部 tool_caller 的 LLM token 不推送为 message--
    _handle_message_chunk 仅认 langgraph_node == "reply_node"。

    P3 契约说明：``thought`` / ``error`` 事件与 ``done.trace_id`` 是对系分
    §6.2.1 事件表的**扩展**（M6-B4 推理过程展示 / P1-3 异常透出 / 链路追踪），
    前端按未知事件忽略处理，不破坏原有契约。
    """
    graph = _get_graph()
    # P2 归属隔离：LangGraph thread_id 与会话锁绑定 (user_id, session_id)，
    # 防止"知道 session_id 即跨用户读/写他人会话 checkpoint 历史"。
    # 外部 session_id 契约不变（done 事件仍返回原始 session_id），thread_key
    # 仅用于 checkpoint 命名空间与锁键；匿名用户统一 anon 前缀。
    user_id = initial_state.get("user_id")
    if session_id:
        # 前缀 user_id（匿名统一 anon），避免跨用户同名 session_id 共享会话
        thread_key = f"{'anon' if user_id is None else user_id}:{session_id}"
    else:
        thread_key = str(uuid4())
    config = {"configurable": {"thread_id": thread_key}}

    logger.info("[SSE] 开始流程执行, thread_id=%s", thread_key)
    streamed_reply = False
    # 本轮助手回复累计文本（供会话元数据落库 last_message）
    reply_text = ""
    # 本轮 LLM token 用量（M6-B4 done.usage）：langgraph 首个 chunk 含
    # input_tokens，后续 chunk 只递增 output_tokens
    usage: dict[str, int] = {"prompt_tokens": 0, "completion_tokens": 0, "total_tokens": 0}
    # subgraphs=True 去重签名集：跨帧累积，防止子图自累积 + 主图层级重复
    # 上报导致同一工具结果/确认卡片被重复推送（方案 B 实时上报）
    seen: set[tuple[str, str, str]] = set()
    # 延迟事件缓冲（M8-6 时序）：options / card 在子图执行中产生，但需在
    # 回复文本流式完毕后再推给前端，保证「先回复说明、再弹卡」的体验。
    # action / observation 仍即时推（工具执行进度需实时反馈）。
    deferred: list[tuple[str, dict[str, Any]]] = []

    # P1-9 会话级串行锁：同 session 并发请求排队执行，防止交错读写同一
    # checkpoint 互相覆盖（checkpointer 锁 per-instance，无法跨请求协调）。
    # 锁持有期间本生成器独占该会话的图执行；不同会话互不影响。
    # P2：锁键同样用 (user_id, session_id) 命名空间，不同用户同 session_id 不共享锁。
    lock = await _get_session_lock(thread_key)
    lock_acquired = False
    try:
        try:
            await lock.acquire()
            lock_acquired = True
        except BaseException:
            # 等待锁期间被取消/客户端断开（GeneratorExit）：未持有锁，
            # 归还引用计数后向上传播，锁不会泄漏。
            await _release_session_lock(thread_key)
            raise

        try:
            async for _namespace, mode, chunk in graph.astream(
                initial_state,
                config=config,
                stream_mode=["messages", "updates"],
                subgraphs=True,
            ):
                if mode == "messages":
                    msg, meta = chunk
                    events, pushed = _handle_message_chunk(msg, meta, usage)
                    if pushed:
                        streamed_reply = True
                        # 累计回复文本：供会话元数据落库（last_message）
                        reply_text += _chunk_text(msg)
                    for event, payload in events:
                        yield _sse(event, payload)
                elif mode == "updates":
                    for event, payload in _handle_updates_chunk(chunk, seen):
                        # options / card 缓冲到回复之后；action / observation 即时推
                        if event in ("options", "card"):
                            deferred.append((event, payload))
                        else:
                            yield _sse(event, payload)
                    # reply_node 完成但未流式时，兜底推送完整回复
                    if "reply_node" in chunk and not streamed_reply:
                        content = _extract_last_content(chunk.get("reply_node", {}))
                        if content:
                            yield _sse("message", {"delta": content})
                            streamed_reply = True

            logger.info("[SSE] 流程完成, thread_id=%s", thread_key)

            # 回复流式结束后，按序推送延迟事件（options / card），保证
            # 「先回复说明、再弹卡」的体验（M8-6 时序修正）。
            for event, payload in deferred:
                yield _sse(event, payload)

            # 会话元数据落库（历史会话列表）：增强功能，失败仅 log，不阻塞 done
            # 主流程。仅正常完成路径执行——异常/断开路径提前 return，不落库
            # （该轮对话未完整，不入列表）。
            try:
                await _record_session(initial_state, session_id, reply_text)
            except Exception:
                logger.exception("会话元数据落库失败, session_id=%s", session_id)

        except GeneratorExit:
            # P1-3 客户端断开：记录后直接结束，不补推 done（无人接收）。
            # 必须 return 而非 raise + finally 中 yield —— 生成器关闭（GeneratorExit
            # 传播）期间在 finally 里 yield 会抛 "generator ignored GeneratorExit"
            # RuntimeError，生产必现（客户端断连刷日志/连接清理异常）。
            logger.warning("[SSE] 客户端断开连接, thread_id=%s", thread_key)
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
        # P2：返回原始 session_id（外部契约），而非内部命名空间的 thread_key。
        _usage = {k: v for k, v in usage.items() if v} or None
        yield _sse(
            "done",
            {"session_id": session_id, "trace_id": trace_id, "usage": _usage},
        )

    finally:
        # P1-9：释放会话锁并归还引用（正常/异常/断开三条路径均安全收敛；
        # release 为同步方法、_release_session_lock 为 await，均不在生成器
        # 关闭期间 yield，不触发 P1-3 的 GeneratorExit 陷阱）。
        if lock_acquired:
            lock.release()
        await _release_session_lock(thread_key)
        # 清理段：仅日志，不 yield。
        logger.info("[SSE] 生成器退出, thread_id=%s", thread_key)


def _sse(event: str, payload: dict[str, Any]) -> str:
    """构造一条 SSE 事件。"""
    return f"event: {event}\ndata: {json.dumps(payload, ensure_ascii=False)}\n\n"


@router.get("/chat/sessions")
async def list_sessions(request: Request) -> JSONResponse:
    """历史会话列表（系分 §6.2 扩展，仅 C 端患者）。

    按 JWT 中间件注入的 ``user_id`` 隔离（跨用户不可见他人会话），返回该用户
    所有 C 端历史 AI 会话（updated_at 倒序，LIMIT 上限）。前端点开任一
    ``session_id`` → 调 POST /api/chat/stream → 既有 checkpointer 机制恢复
    该会话上下文续聊（**历史会话切换自此闭环**）。

    Args:
        request: 请求（JWT 中间件注入 user_id；匿名时未注入）。

    Returns:
        统一信封 {code, message, data: {sessions: [...]}}；未登录 401。
    """
    user_id = getattr(request.state, "user_id", None)
    if user_id is None:
        return error_response(
            "AUTH_MISSING",
            "缺少有效的鉴权 Token",
            getattr(request.state, "trace_id", ""),
            401,
        )
    try:
        sessions = await get_session_store().list_by_user(user_id)
    except Exception:
        logger.exception("会话列表查询失败, user_id=%s", user_id)
        return error_response(
            "SERVER_ERROR",
            "服务异常，请稍后重试",
            getattr(request.state, "trace_id", ""),
            500,
        )
    return success_response({"sessions": sessions}, getattr(request.state, "trace_id", ""))


@router.delete("/chat/sessions/{session_id}")
async def delete_session(session_id: str, request: Request) -> JSONResponse:
    """删除历史会话（系分 §6.2 扩展，仅 C 端患者）。

    删除该会话的元数据条目（agent_sessions）+ checkpoint 历史消息，使其从
    历史会话列表消失且切换进去不再有历史消息。按 ``user_id`` 隔离，跨用户
    不可删他人会话。

    Args:
        session_id: 路径参数，待删会话 ID。
        request: 请求（JWT 中间件注入 user_id；匿名时未注入）。

    Returns:
        统一信封 {code, message, data: {session_id}}；未登录 401；会话不存在 404。
    """
    user_id = getattr(request.state, "user_id", None)
    trace_id = getattr(request.state, "trace_id", "")
    if user_id is None:
        return error_response("AUTH_MISSING", "缺少有效的鉴权 Token", trace_id, 401)
    # 删元数据（按 user_id 隔离，跨用户删不到他人会话）
    try:
        deleted = await get_session_store().delete(user_id, session_id)
    except Exception:
        logger.exception("会话删除失败, user_id=%s session_id=%s", user_id, session_id)
        return error_response("SERVER_ERROR", "服务异常，请稍后重试", trace_id, 500)
    # 删 checkpoint 历史消息：thread_key 与 _sse_generator 一致（user_id:session_id）。
    # 即使元数据未落库（deleted=False）仍尝试清理可能残留的 checkpoint；thread_key
    # 含 user_id，删不到他人会话，安全。失败仅 log，不阻塞删除主流程。
    try:
        await _get_graph().checkpointer.adelete_thread(f"{user_id}:{session_id}")
    except Exception:
        logger.warning("删除 checkpoint 失败, session_id=%s", session_id)
    if not deleted:
        return error_response("SESSION_NOT_FOUND", "会话不存在", trace_id, 404)
    return success_response({"session_id": session_id}, trace_id)


@router.get("/chat/sessions/{session_id}/messages")
async def get_session_messages(session_id: str, request: Request) -> JSONResponse:
    """历史会话消息（系分 §6.2 扩展，仅 C 端患者）。

    前端切换到历史会话后调此接口拉取该会话历史消息，渲染对话气泡。从
    checkpointer 读取 thread_id 对应的最新 state（含完整 messages 历史），
    按 user_id 隔离（thread_key 含 user_id，跨用户读不到他人会话）。

    依赖 ``checkpointer_backend=postgres``（生产持久化）；memory 后端进程
    重启即失，重启后历史消息为空（开发已知限制）。

    Args:
        session_id: 路径参数，历史会话 ID。
        request: 请求（JWT 中间件注入 user_id；匿名时未注入）。

    Returns:
        统一信封 {code, message, data: {messages: [{role, content}]}}；未登录 401。
    """
    user_id = getattr(request.state, "user_id", None)
    trace_id = getattr(request.state, "trace_id", "")
    if user_id is None:
        return error_response("AUTH_MISSING", "缺少有效的鉴权 Token", trace_id, 401)
    # thread_key 与 _sse_generator 一致（user_id:session_id）
    config = {"configurable": {"thread_id": f"{user_id}:{session_id}"}}
    try:
        snapshot = await _get_graph().aget_state(config)
    except Exception:
        logger.exception("历史消息读取失败, session_id=%s", session_id)
        return error_response("SERVER_ERROR", "服务异常，请稍后重试", trace_id, 500)
    messages = (snapshot.values or {}).get("messages", [])
    return success_response(
        {"messages": _serialize_session_messages(messages)},
        trace_id,
    )


def _chunk_text(msg: Any) -> str:
    """提取 message chunk 的文本内容（str 或 LangChain 内容块列表）。

    Args:
        msg: messages 模式下的消息 chunk（BaseMessage）。

    Returns:
        str: 拼接后的文本（无文本块返回空串）。
    """
    content = getattr(msg, "content", None) or ""
    if isinstance(content, str):
        return content
    return _content_blocks_to_text(content)


def _content_blocks_to_text(blocks: Any) -> str:
    """LangChain 内容块列表转文本（提取 type=text 块拼接，系分 §6.2 扩展）。

    Args:
        blocks: content 值（list[dict] / 其他）；非 list 统一 str() 兜底。

    Returns:
        str: 拼接后的文本。
    """
    if not isinstance(blocks, list):
        return str(blocks) if blocks is not None else ""
    parts = []
    for block in blocks:
        if isinstance(block, dict) and block.get("type") == "text":
            parts.append(block.get("text", ""))
    return "".join(parts)


def _serialize_session_messages(messages: Any) -> list[dict[str, str]]:
    """将 state messages 序列化为前端 {role, content} 列表（系分 §6.2 扩展）。

    仅保留 user / assistant（过滤 system 注入与 tool 中间态），兼容 dict
    与 LangChain BaseMessage 两种存储形态，content 统一转文本。

    Args:
        messages: state.values["messages"]（dict 或 BaseMessage 列表）。

    Returns:
        list[dict[str, str]]: {role: "user"|"assistant", content: str} 列表。
    """
    result: list[dict[str, str]] = []
    for m in messages:
        role, content = _extract_message_role_content(m)
        if role in ("user", "assistant") and content:
            result.append({"role": role, "content": content})
    return result


def _extract_message_role_content(m: Any) -> tuple[str, str]:
    """提取单条消息 (role, content 文本)，兼容 dict 与 BaseMessage。

    BaseMessage.type 映射：human->user, ai->assistant；其余原样返回。

    Args:
        m: 消息（dict 或 LangChain BaseMessage）。

    Returns:
        tuple[str, str]: (role, content 文本)。
    """
    if isinstance(m, dict):
        role = str(m.get("role", ""))
        raw = m.get("content", "")
    else:
        msg_type = getattr(m, "type", "")
        role = {"human": "user", "ai": "assistant"}.get(msg_type, msg_type)
        raw = getattr(m, "content", "")
    text = raw if isinstance(raw, str) else _content_blocks_to_text(raw)
    return role, text


def _truncate_title(text: str, max_len: int = 30) -> str:
    """截断会话标题/摘要（折叠换行，超长加省略号）。

    Args:
        text: 原始文本。
        max_len: 截断上限（默认 30 字）。

    Returns:
        str: 截断后的单行文本。
    """
    text = " ".join(text.strip().split())
    return text if len(text) <= max_len else f"{text[:max_len]}…"


async def _record_session(
    initial_state: AgentState, session_id: str | None, reply_text: str
) -> None:
    """落库一轮会话元数据（历史会话列表，系分 §6.2 扩展）。

    从 ``initial_state`` 取 user_id / scope / 首条用户消息（title 来源；
    ``_build_initial_state`` 首条即本轮用户消息，但 confirmed_actions 会向首位
    注入 system 消息，故循环找第一个 role=user 消息），``reply_text`` 为本轮
    累计的助手回复文本（last_message）。

    Args:
        initial_state: ``_build_initial_state`` 构造的初始状态。
        session_id: 本轮会话 ID（原始，非内部 thread_key）；None 时跳过落库。
        reply_text: 本轮助手回复累计文本（可为空——无流式回复时 last_message 置 None）。

    Raises:
        Exception: 存储层失败时上抛，由调用方 try/except 包裹（不阻塞对话主流程）。
    """
    if not session_id:
        return
    user_id = initial_state.get("user_id")
    scope = initial_state.get("scope", "c_end")
    first_user_msg = ""
    for m in initial_state.get("messages", []):
        if isinstance(m, dict):
            if m.get("role") == "user":
                first_user_msg = m.get("content") or ""
                break
        else:
            if getattr(m, "type", "") == "human" or str(getattr(m, "role", "")).lower() == "user":
                first_user_msg = getattr(m, "content", "") or ""
                break
    store = get_session_store()
    await store.upsert(
        user_id=user_id,
        session_id=session_id,
        scope=scope,
        title=_truncate_title(first_user_msg) if first_user_msg else "新会话",
        last_message=_truncate_title(reply_text) if reply_text else None,
        message_count=1,
    )


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
    "save_pre_consultation": "确认提交预问诊给医生",
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
    "confirm_pre_consultation": ("doctor_id", "chief_complaint"),
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
    Agent 读取 confirm_token 记录（Redis GET，不删除），校验 session/user，
    执行对应的 MCP 工具；成功后才删除 token 并写回执，失败保留 token 供
    用户重试（复用同一幂等键，Java 侧去重防重复执行业务）。

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

    # P2 #17：读取 confirm_token 记录（GET 不删）。删除推迟到工具执行成功后，
    # 失败时保留 token 供用户重试；并发双击由幂等键 + Java 去重兜底。
    try:
        record = await get_confirm_token_by_token(
            session_id=req.session_id,
            token_id=req.confirm_token,
            expected_user_id=user_id,
        )
    except Exception:
        # Redis 不可用
        logger.error("confirm_token 读取异常: Redis 不可用")
        return _confirm_error("CONFIRM_INVALID", "操作暂时不可用，请稍后重试", trace_id)

    if record is None:
        # token 不存在 / 已过期 / 用户不匹配
        return _confirm_error("CONFIRM_EXPIRED", "操作已超时或无效，请重新发起", trace_id)

    # session 校验（record 里的 session 应与请求一致）
    if record.get("session_id") != req.session_id:
        return _confirm_error("SESSION_MISMATCH", "会话不匹配，请刷新重试", trace_id)

    # 执行对应的 L2 工具（匿名请求无 user_id，用 getattr 兜底）
    tool_name = record.get("tool_name", "")
    arguments = record.get("tool_arguments", {})
    # P2 #17：复用 confirm_token 记录的幂等键——同一确认操作（含失败重试）
    # Java 侧按 X-Idempotency-Key 去重，防止"已提交但响应超时 -> 重试 ->
    # 重复执行业务"（挂号锁定/购药下单等写操作）。
    idempotency_key = record.get("idempotency_key")
    # execute_mcp_tool 只消费 user_id/scope/session_id，无需完整 AgentState，
    # cast 表明这是按需构造的部分状态
    state = cast(
        AgentState,
        {
            "user_id": getattr(request.state, "user_id", None),
            "scope": getattr(request.state, "scope", "c_end"),
            "session_id": req.session_id,
            # JWT 透传（C 端拦截器硬需求）：L2 确认执行的工具调用经
            # call_java_api 需 Authorization: Bearer；中间件已注入 jwt_token。
            "jwt_token": getattr(request.state, "jwt_token", None),
        },
    )
    # P2 审计溯源：人工点击确认触发的 L2 工具执行，审计标记
    # confirm_method=click / trigger=manual，区别于 Agent 自主调用
    result = await execute_mcp_tool(
        tool_name,
        arguments,
        state,
        confirm_method="click",
        trigger="manual",
        idempotency_key=idempotency_key,
    )

    if not result.get("success"):
        error = result.get("error", {})
        # P2 #17：执行失败保留 confirm_token（不删），用户可携带原卡片重试，
        # 复用同一幂等键，Java 去重不重复执行业务。
        # TOOL_FAILED 系分 §6.1 错误码表标注为 500。
        # M8-9：Java 业务冲突/校验错误码（A 开头，如 A0443"已有进行中问诊"）
        # 是**预期业务拦截**而非系统故障——用 200 返回 + 明确 message，前端
        # 能正常展示错误提示（如"当前医生仍有进行中的预问诊"），而非 500 崩溃态。
        err_code = error.get("code", "")
        err_message = error.get("message", "操作执行失败")
        if str(err_code).startswith("A"):
            return _confirm_error("BUSINESS_CONFLICT", err_message, trace_id, status_code=200)
        return _confirm_error(
            "TOOL_FAILED", err_message, trace_id, status_code=500
        )

    # P2 #17：执行成功后删除 confirm_token（一次性语义收敛到此处），
    # 用户无法再用已成功的卡片重复操作。
    try:
        await delete_confirm_token_by_token(
            session_id=req.session_id,
            token_id=req.confirm_token,
            expected_user_id=user_id,
        )
    except Exception:
        logger.warning("删除 confirm_token 失败: token=%s", req.confirm_token)

    # M5-T4（T-M3-L1）：写入确认成功回执，供下一轮对话引用（此操作是独立
    # HTTP 请求，结果不进 graph 状态；Redis 回执 + 下轮注入保证对话连续）
    # P2 安全：写入归属 user_id，供下一轮消费时按归属过滤。
    try:
        await set_confirm_done(req.session_id, tool_name, result.get("data"), user_id)
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
    return error_response(code, message, trace_id, status_code)


def _success_message(tool_name: str) -> str:
    """L2 工具执行成功的面向用户提示（系分 §6.2.2 data.message）。"""
    messages = {
        "create_appointment": "挂号成功，请及时完成支付",
        "cancel_appointment": "挂号已取消",
        "save_pre_consultation": "预问诊已提交给医生",
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


# 已确认操作结果摘要（P2）：action_result 中提取的关键业务 ID 字段
_RESULT_ID_KEYS = (
    "order_id",
    "appointment_id",
    "drug_order_id",
    "report_id",
    "record_id",
    "plan_id",
    "consultation_id",
)
_ID_KEY_LABELS = {
    "order_id": "单号",
    "appointment_id": "单号",
    "drug_order_id": "单号",
    "report_id": "报告编号",
    "record_id": "记录编号",
    "plan_id": "计划编号",
    "consultation_id": "记录编号",
}


def _extract_result_brief(data: Any) -> str:
    """从 action_result 提取一行关键摘要（如"单号 999"），无则返回空串。

    仅提取 ID 类字段（含一层包裹结构），避免把完整嵌套数据（如检查报告
    详情等 PHI 内容）整体注入下一轮 LLM 上下文；业务 ID 已足够支撑
    "我的挂号单号是多少"式追问。
    """
    if not isinstance(data, dict):
        return ""
    # 直接层 + 一层包裹（Java 信封 data / 通用包装）两处找 ID 字段
    layers: list[dict[str, Any]] = [data]
    layers.extend(v for v in data.values() if isinstance(v, dict))
    for layer in layers:
        for k, v in layer.items():
            if k in _RESULT_ID_KEYS and v not in (None, ""):
                return f"{_ID_KEY_LABELS.get(k, k)} {v}"
    return ""


def _summarize_confirmed_action(action: dict[str, Any]) -> str:
    """生成单条已确认操作的摘要：操作名（操作结果，关键 ID）。

    Args:
        action: confirm_done 回执记录（tool_name / action_result）。

    Returns:
        str: 供注入 system 上下文的一行摘要。
    """
    tool_name = action.get("tool_name", "")
    label = _TOOL_LABELS.get(tool_name, tool_name or "操作")
    result_msg = _success_message(tool_name)
    brief = _extract_result_brief(action.get("action_result"))
    if brief:
        return f"{label}（{result_msg}，{brief}）"
    return f"{label}（{result_msg}）"


def _build_confirmed_actions_system(confirmed_actions: list[dict[str, Any]]) -> str:
    """构造已确认操作注入的 system 内容（P2：含操作结果摘要）。"""
    summaries = [_summarize_confirmed_action(t) for t in confirmed_actions]
    return (
        "本会话此前用户已确认完成以下操作："
        + "；".join(summaries)
        + "。后续用户提及这些操作时，基于已执行结果回答，不要重复要求确认。"
    )
