"""购药子图（系分 §5.2.1）。

调 query_pharmacy_stock → create_drug_order（含 L2 确认）。
"""

from langgraph.graph import StateGraph, END

from app.orchestrator.state import AgentState
from app.orchestrator.nodes.tool_caller import tool_caller
from app.orchestrator.nodes.safety import safety_check
from app.orchestrator.nodes.tool_executor import tool_executor


def route_safety(state: AgentState) -> str:
    if state.get("pending_confirmation"):
        return "pending_confirm"
    return "execute"


def build_pharmacy_graph():
    """构造购药子图。"""
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

    return builder
