"""工具调用决策节点（系分 §5.1 / §5.2.2）。

LLM 决策选择哪个 Function Calling 工具及参数，返回 ``tool_calls`` 供
safety_check / tool_executor 消费。

安全策略（双层防护）：
    1. 绑定 L1/L2 工具给 LLM（L3/L4 不注册）
    2. 即便 LLM 返回 L3/L4 工具名，也过滤掉，只放行 L1/L2
L2 工具由 safety_check 拦截生成确认卡片；L3/L4 被双层过滤。
LLM 未选工具或解析失败时降级为 ``tool_calls=[]``，由回复节点兜底，不阻塞流程。
"""

import logging
from datetime import date
from typing import Any

from app.engine.llm.factory import build_llm
from app.engine.memory.buffer import truncate_messages
from app.engine.tools.schema_registry import SecurityLevel, ToolRegistry, ToolScope
from app.infrastructure.config.settings import get_settings
from app.orchestrator.state import AgentState

logger = logging.getLogger(__name__)

# 工具决策系统提示词
TOOL_CALLER_SYSTEM_PROMPT = """你是医疗平台的工具调用助手。

当前日期：{today}（服务器本地日期，YYYY-MM-DD）

你可以使用以下工具来完成用户请求（只使用列表内的工具）：

{tools_desc}

规则：
1. 当用户请求涉及业务操作（创建/修改/取消/查询）时，必须调用对应的工具完成，不要仅凭知识回复
2. 无依赖的工具可一次并行调用；有依赖的工具分步调用：
   - 查科室、查医生、查排班等查询可一次并行
   - 创建类工具（如创建挂号）的必填参数（如 slot_id）来自查询结果，
     必须先执行查询、等结果返回后再调用，禁止在首次并行中编造该参数
3. 参数严格按工具定义填写，缺失的信息先询问用户，或等上一轮工具结果返回后再决策
4. 不要编造工具名或参数
5. **日期参数必须用当前日期或之后的日期**：用户说"今天"即 {today}；说"X月X日"若未给出年份，
   默认当年，且不得早于今天。禁止编造过去日期
6. 工具调用结果会自动返回，不需要让用户等待重试
7. 创建/修改/取消类操作（如创建挂号、取消挂号）在拿到所需参数后**直接调用对应工具**，
   不要用自然语言反问用户"是否确认"——用户请求即代表发起授权，系统会通过确认卡片
   让用户最终确认，你只需调用工具即可
8. **完成判断（每次决策前先检查）**：
   - 如果上一步工具结果已返回用户所需的全部信息，**停止调用工具**
   - 如果用户请求包含创建/修改/取消操作（如"挂X的号"），查询步骤只是前置，
     拿到所需参数后**必须继续**调用对应创建/修改/取消工具，不要提前停止
   - 不要重复调用已执行过的工具（相同参数、相同目的）
9. 一次只推进一个必要的查询/操作步骤，避免一次轮询所有信息"""


def _build_tools_prompt(tools: list[dict[str, Any]]) -> str:
    """将工具 Schema 列表格式化为 prompt 描述。"""
    lines = []
    for tool in tools:
        fn = tool.get("function", {})
        name = fn.get("name", "")
        desc = fn.get("description", "")
        params = fn.get("parameters", {})
        props = params.get("properties", {})
        required = params.get("required", [])
        param_desc = ", ".join(f"{k}({v.get('type', 'any')})" for k, v in props.items())
        req_mark = "必填" if required else "可选"
        lines.append(f"- {name}: {desc} | 参数: {param_desc} | {req_mark}")
    return "\n".join(lines)


async def tool_caller(state: AgentState, allowed_tools: list[str] | None = None) -> dict[str, Any]:
    """LLM 决定调用工具，返回 ``{"tool_calls": [...]}``。

    从 ``ToolRegistry`` 取当前 scope 的 L1/L2 工具 Schema，绑定到 LLM，
    解析返回的 ``tool_calls`` 写入状态。安全校验在此完成：L3/L4 工具被过滤
    （L2 由 safety_check 拦截生成确认）。

    子图分化（系分 §5.2.1）：``allowed_tools`` 为业务子图注入的工具白名单，
    非 None 时只绑定白名单内的 L1/L2 工具，让导诊/挂号/问诊/购药各子图
    只暴露本场景工具，减少 LLM 误选其他业务创建型操作。

    ⚠️ 白名单仅约束 C 端：四个业务子图的白名单（如 ``TRIAGE_TOOLS``）是 C 端
    患者场景专属，不含任何 B 端工具名。若对 B 端 scope 也按白名单过滤，B 端
    医生流程的全部工具会被滤空（``tool_calls=[]``，LLM 无工具可调）。因此
    B 端保持全量绑定当前 scope 的 L1/L2 工具（M5 验收"B 端 4 场景跑通"依赖此）。

    Args:
        state: 当前图状态，包含 scope / messages 字段。
        allowed_tools: 子图工具白名单（工具名列表）；None 表示不限（默认全量）。
            仅 C 端生效，B 端忽略该参数。

    Returns:
        dict: 部分状态更新，包含 tool_calls（LLM 选择的 L1/L2 工具列表，
            格式为 ``[{"name": ..., "arguments": {...}}, ...]``）。

    Raises:
        无：LLM 调用失败时降级为 ``tool_calls=[]``，由回复节点兜底。
    """
    scope = state.get("scope", "c_end")
    try:
        tool_scope = ToolScope(scope)
    except ValueError:
        logger.warning("未知 scope=%s，默认使用 c_end 工具集", scope)
        tool_scope = ToolScope.C_END

    # 取 L1+L2 工具（L3/L4 不注册；L2 由 safety_check 拦截生成确认），转 OpenAI schema
    subgraph_tools = ToolRegistry.get_tools_by_scope(tool_scope)
    # 白名单仅约束 C 端：业务子图白名单是 C 端场景专属，B 端忽略白名单，
    # 否则 B 端工具（query_patient_history 等 8 个）会被全部滤空不可达
    effective_allowed = allowed_tools if tool_scope == ToolScope.C_END else None
    if effective_allowed is not None:
        allowed_set = set(effective_allowed)
        subgraph_tools = [t for t in subgraph_tools if t.name in allowed_set]
    tools = [
        t.to_openai_schema()
        for t in subgraph_tools
        if t.security_level in (SecurityLevel.L1, SecurityLevel.L2)
    ]
    if not tools:
        logger.warning("scope=%s 无可用 L1/L2 工具", scope)
        return {"tool_calls": []}

    llm = build_llm()
    llm_with_tools = llm.bind_tools(tools)

    history = truncate_messages(state.get("messages", []), get_settings().memory_window_size)
    system_prompt = TOOL_CALLER_SYSTEM_PROMPT.format(
        tools_desc=_build_tools_prompt(tools), today=date.today().isoformat()
    )
    messages = [{"role": "system", "content": system_prompt}] + history

    # 注入已执行工具的结果（子图循环累积了前面所有轮次，LLM 分步决策可见）
    tool_results = state.get("tool_results")
    if tool_results:
        from app.orchestrator.nodes.reply import _format_tool_results

        summary = _format_tool_results(tool_results)
        messages.append({"role": "system", "content": f"已执行的工具结果：\n{summary}"})

    try:
        response = await llm_with_tools.ainvoke(messages)
        tool_calls = _extract_tool_calls(response, tool_scope, effective_allowed)
        # 软兜底：拦截「相同参数 + 上次已成功」的重复调用（LLM 提示词收敛不可靠，
        # 这里做确定性去重——循环问题 P3-6）。意外截断/失败的重试放行，操作型 L2
        # 不进入 tool_results 天然豁免。M8-1：同时对比 pending_confirmations，
        # 源头拦截子图循环第二轮重复返回同一 L2（防两张 token 互异的确认卡）。
        tool_calls = _dedupe_tool_calls(
            tool_calls,
            state.get("tool_results") or [],
            state.get("pending_confirmations") or [],
        )
        logger.info(
            "工具决策: scope=%s, 选择 %d 个工具: %s",
            scope,
            len(tool_calls),
            [tc["name"] for tc in tool_calls],
        )
        iteration = state.get("tool_iteration") or 0
        return {"tool_calls": tool_calls, "tool_iteration": iteration + 1}
    except Exception as e:
        logger.error("工具决策失败: %s", e)
        return {"tool_calls": []}


def _dedupe_tool_calls(
    tool_calls: list[dict[str, Any]],
    executed: list[dict[str, Any]],
    pending: list[dict[str, Any]] | None = None,
) -> list[dict[str, Any]]:
    """过滤重复工具调用（确定性防循环 P3-6 + 防重复挂起 M8-1）。

    两类「重复」判定：
    1. 与已执行结果重复（L1 防循环）：工具名相同 + 参数完全一致 + 上次执行已成功
       - 上次失败/超时（success=False）→ 模型自主重试合理，放行
       - 参数不同 → 合法多步推进（换科室/换日期），放行
    2. 与待确认 L2 重复（M8-1 防重复挂起）：工具名相同 + 参数完全一致且
       已在 ``pending_confirmations`` 中 → 剔除，不重复发起同一 L2
       （避免子图循环第二轮 LLM 重复返回同一 L2，safety_check 又生成新
       token，导致两张 token 互异的确认卡 → 用户双确认 = 同一业务执行两次）

    Args:
        tool_calls: LLM 本轮要调用的工具列表（[{name, arguments}]）。
        executed: 本轮对话子图循环已执行的工具结果列表（含 tool_name /
            arguments / success 字段）。
        pending: 待用户确认的 L2 操作列表（safety_check 写入，含 tool_name /
            tool_arguments 字段）。None 或空时不做 L2 去重。

    Returns:
        list[dict]: 过滤后的工具调用列表，重复项被剔除。
    """
    if not executed and not pending:
        return tool_calls
    pending = pending or []
    deduped: list[dict[str, Any]] = []
    for call in tool_calls:
        name = call["name"]
        args = call.get("arguments") or {}
        # L1 防循环：与已执行成功结果重复 → 剔除
        is_executed_dup = any(
            prev.get("tool_name") == name
            and (prev.get("arguments") or {}) == args
            and prev.get("success")
            for prev in executed
        )
        # M8-1 防 L2 重复挂起：相同 tool_name+args 已在 pending → 剔除
        # （pending 项参数键为 tool_arguments，与 executed 的 arguments 区分）
        is_pending_dup = any(
            p.get("tool_name") == name
            and (p.get("tool_arguments") or {}) == args
            for p in pending
        )
        if is_executed_dup:
            logger.info("去重重复工具调用: %s %s（上次已成功）", name, args)
        elif is_pending_dup:
            logger.info("去重重复 L2 挂起: %s %s（已在 pending_confirmations）", name, args)
        else:
            deduped.append(call)
    return deduped


def _extract_tool_calls(
    response: Any,
    tool_scope: ToolScope,
    allowed_tools: list[str] | None = None,
) -> list[dict[str, Any]]:
    """从 LLM 响应中提取并过滤 tool_calls（只放行 L1/L2）。

    Args:
        response: LLM ainvoke 返回值（含 tool_calls 属性）。
        tool_scope: 当前服务端，用于校验工具是否存在。
        allowed_tools: 子图工具白名单；非 None 时只放行白名单内工具。
            仅 C 端传入（B 端由调用方置 None，白名单不约束 B 端）。

    Returns:
        list[dict]: 过滤后的 L1/L2 工具调用列表（L3/L4 拦截）。
    """
    calls = getattr(response, "tool_calls", None) or []
    allowed_set = set(allowed_tools) if allowed_tools is not None else None
    result: list[dict[str, Any]] = []
    for call in calls:
        # 兼容对象（langchain ToolCall）与 dict 两种格式（不同 LLM 返回不同）
        if isinstance(call, dict):
            name = call.get("name", "")
            args = call.get("args", {}) or {}
        else:
            name = getattr(call, "name", "")
            args = getattr(call, "args", {}) or {}
        tool = ToolRegistry.get_tool(name)
        # 双重过滤：工具必须存在且为 L1/L2（即便 LLM 返回 L3/L4 也拦截），
        # 且若子图限定了白名单，只放行白名单内的工具
        is_allowed = (
            tool is not None
            and tool.scope == tool_scope
            and tool.security_level in (SecurityLevel.L1, SecurityLevel.L2)
            and (allowed_set is None or name in allowed_set)
        )
        if is_allowed:
            result.append({"name": name, "arguments": args})
        else:
            logger.warning("过滤非 L1/L2 工具调用: %s", name)
    return result
