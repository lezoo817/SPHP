"""业务子图共享构造（系分 §5.2.1）。

4 个业务子图（导诊 / 挂号 / 问诊 / 购药）共享
``tool_caller -> safety_check -> tool_executor`` 的骨架，差异在于各自注入的
工具白名单（tool_names）——不同业务场景只绑定本场景的 L1/L2 工具，
减少 LLM 误选其他业务的创建型操作，prompt 更聚焦。

子图循环：tool_executor 后 route_continue 判断是否需继续调用工具。
如 LLM 返回更多 tool_calls 则循环回 tool_caller，最多 5 轮。
"""

from typing import Any

from langchain_core.runnables import RunnableConfig
from langgraph.graph import END, StateGraph

from app.infrastructure.config.settings import get_settings
from app.orchestrator.nodes.safety import safety_check
from app.orchestrator.nodes.tool_caller import tool_caller
from app.orchestrator.nodes.tool_executor import tool_executor
from app.orchestrator.state import AgentState


def route_safety(state: AgentState) -> str:
    """L1 → tool_executor（立即执行），仅 L2 无 L1 → pending_confirm（挂起）。"""
    has_tool_calls = bool(state.get("tool_calls"))
    has_pending = bool(state.get("pending_confirmations"))
    if has_tool_calls:
        # 有 L1 工具：先执行，pending_confirmations 保留在 state 中
        return "execute"
    if has_pending:
        # 仅有 L2 待确认，无 L1 工具：挂起等待用户确认
        return "pending_confirm"
    return "execute"


def route_continue(state: AgentState) -> str:
    """tool_executor 后判断是否继续调用工具。

    如果 LLM 返回了新的 tool_calls 且未超最大迭代次数（settings.max_tool_iterations，
    防 LLM 无限循环），循环回 tool_caller；否则结束子图。

    ⚠️ M8-1 注释：此处 ``tool_calls`` 为 safety_check 放行的 L1（executor 已执行），
    语义上 stale，但作为「让 LLM 基于 tool_results 继续分步决策」的循环信号有效
    （如查号源后继续创建挂号）。重复 L2 不靠此处置，由 ``_dedupe_tool_calls``
    对比 pending_confirmations + ``safety_check`` 复用 token 在源头拦截，确保
    同一 L2 恰好一张确认卡。
    """
    iteration = state.get("tool_iteration") or 0
    has_tool_calls = bool(state.get("tool_calls"))
    if has_tool_calls and iteration < get_settings().max_tool_iterations:
        return "continue"
    return "end"


def _bind_tool_caller(
    allowed_tools: list[str] | None, scene_prompt: str | None = None
) -> Any:
    """构造绑定工具白名单与场景提示词的 tool_caller 节点函数（闭包，兼容 LangGraph 传参）。

    Args:
        allowed_tools: 子图工具白名单；None 表示不限（绑定 scope 全量 L1/L2）。
        scene_prompt: 场景专属指令（系分 §5.11），透传给 tool_caller 注入；
            None 表示不注入（通用场景）。

    Returns:
        Callable: 接收 (state, config) 的异步节点函数。
    """

    async def _caller(state: AgentState, config: RunnableConfig | None = None) -> dict[str, Any]:
        """子图入口节点：以绑定白名单与场景提示词调用 tool_caller（闭包兼容 LangGraph 传参）。

        Args:
            state: 图状态。
            config: LangGraph 运行时配置（子图执行时传入，此处透传未用）。

        Returns:
            dict: tool_caller 的状态更新。
        """
        return await tool_caller(
            state, allowed_tools=allowed_tools, scene_prompt=scene_prompt
        )

    return _caller


def build_tool_subgraph(
    tool_names: list[str] | None = None, scene_prompt: str | None = None
) -> Any:
    """构造 tool_caller -> safety -> tool_executor -> (循环/结束) 子图。

    Args:
        tool_names: 子图工具白名单（系分 §5.2.1 各业务场景专属工具集），
            各业务子图文件传入各自的工具名列表；None 表示不限定。
        scene_prompt: 场景专属指令（系分 §5.11 场景指令），注入 tool_caller
            引导 LLM 按场景流程推进（如问诊场景的预问诊/处方解读流程）；
            None 表示不注入（通用场景，向后兼容）。

    Returns:
        Any: 编译后的 LangGraph CompiledGraph。
    """
    builder = StateGraph(AgentState)
    builder.add_node("tool_caller", _bind_tool_caller(tool_names, scene_prompt))
    builder.add_node("safety_check", safety_check)
    builder.add_node("tool_executor", tool_executor)

    builder.set_entry_point("tool_caller")
    builder.add_edge("tool_caller", "safety_check")
    builder.add_conditional_edges(
        "safety_check",
        route_safety,
        {"execute": "tool_executor", "pending_confirm": END},
    )
    builder.add_conditional_edges(
        "tool_executor",
        route_continue,
        {"continue": "tool_caller", "end": END},
    )

    return builder.compile()
