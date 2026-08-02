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
from typing import Any

from app.engine.llm.factory import build_llm
from app.engine.memory.buffer import truncate_messages
from app.engine.tools.schema_registry import SecurityLevel, ToolRegistry, ToolScope
from app.infrastructure.config.settings import get_settings
from app.orchestrator.state import AgentState

logger = logging.getLogger(__name__)

# 工具决策系统提示词
TOOL_CALLER_SYSTEM_PROMPT = """你是医疗平台的工具调用助手。

你可以使用以下工具来完成用户请求（只使用列表内的工具）：

{tools_desc}

规则：
1. 只有当确实需要查询业务数据时才调用工具，否则不调用
2. 一次性调用所有需要的工具（可并行）
3. 参数严格按工具定义填写，缺失的信息先询问用户
4. 不要编造工具名或参数
"""


def _build_tools_prompt(tools: list[dict]) -> str:
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


async def tool_caller(state: AgentState) -> dict[str, Any]:
    """LLM 决定调用工具，返回 ``{"tool_calls": [...]}``。

    从 ``ToolRegistry`` 取当前 scope 的 L1/L2 工具 Schema，绑定到 LLM，
    解析返回的 ``tool_calls`` 写入状态。安全校验在此完成：L3/L4 工具被过滤
    （L2 由 safety_check 拦截生成确认）。

    Args:
        state: 当前图状态，包含 scope / messages 字段。

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
    tools = [
        t.to_openai_schema()
        for t in ToolRegistry.get_tools_by_scope(tool_scope)
        if t.security_level in (SecurityLevel.L1, SecurityLevel.L2)
    ]
    if not tools:
        logger.warning("scope=%s 无可用 L1/L2 工具", scope)
        return {"tool_calls": []}

    llm = build_llm()
    llm_with_tools = llm.bind_tools(tools)

    history = truncate_messages(state.get("messages", []), get_settings().memory_window_size)
    system_prompt = TOOL_CALLER_SYSTEM_PROMPT.format(tools_desc=_build_tools_prompt(tools))
    messages = [{"role": "system", "content": system_prompt}] + history

    try:
        response = await llm_with_tools.ainvoke(messages)
        tool_calls = _extract_tool_calls(response, tool_scope)
        logger.info(
            "工具决策: scope=%s, 选择 %d 个工具: %s",
            scope,
            len(tool_calls),
            [tc["name"] for tc in tool_calls],
        )
        return {"tool_calls": tool_calls}
    except Exception as e:
        logger.error("工具决策失败: %s", e)
        return {"tool_calls": []}


def _extract_tool_calls(response: Any, tool_scope: ToolScope) -> list[dict]:
    """从 LLM 响应中提取并过滤 tool_calls（只放行 L1/L2）。

    Args:
        response: LLM ainvoke 返回值（含 tool_calls 属性）。
        tool_scope: 当前服务端，用于校验工具是否存在。

    Returns:
        list[dict]: 过滤后的 L1/L2 工具调用列表（L3/L4 拦截）。
    """
    calls = getattr(response, "tool_calls", None) or []
    result: list[dict] = []
    for call in calls:
        # 兼容对象（langchain ToolCall）与 dict 两种格式（不同 LLM 返回不同）
        if isinstance(call, dict):
            name = call.get("name", "")
            args = call.get("args", {}) or {}
        else:
            name = getattr(call, "name", "")
            args = getattr(call, "args", {}) or {}
        tool = ToolRegistry.get_tool(name)
        # 双重过滤：工具必须存在且为 L1/L2（即便 LLM 返回 L3/L4 也拦截）
        is_allowed = (
            tool is not None
            and tool.scope == tool_scope
            and tool.security_level in (SecurityLevel.L1, SecurityLevel.L2)
        )
        if is_allowed:
            result.append({"name": name, "arguments": args})
        else:
            logger.warning("过滤非 L1/L2 工具调用: %s", name)
    return result
