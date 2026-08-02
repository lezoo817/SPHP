"""业务子图共享构造（系分 §5.2.1）。

4 个业务子图（导诊 / 挂号 / 问诊 / 购药）共享
``tool_caller -> safety_check -> tool_executor`` 的骨架，差异在于后续注入的
工具集（待子图分化时在各自文件覆盖）。
"""

from typing import Any

from langgraph.graph import END, StateGraph

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


def build_tool_subgraph() -> Any:
    """构造 tool_caller -> safety -> tool_executor 子图（编译后）。"""
    builder = StateGraph(AgentState)
    builder.add_node("tool_caller", tool_caller)
    builder.add_node("safety_check", safety_check)
    builder.add_node("tool_executor", tool_executor)

    builder.set_entry_point("tool_caller")
    builder.add_edge("tool_caller", "safety_check")
    builder.add_conditional_edges(
        "safety_check",
        route_safety,
        {"execute": "tool_executor", "pending_confirm": END},
    )
    builder.add_edge("tool_executor", END)

    return builder.compile()
