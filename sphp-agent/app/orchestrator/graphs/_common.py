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
    """L1 -> tool_executor, L2 -> 等待确认（挂起）。"""
    if state.get("pending_confirmation"):
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
