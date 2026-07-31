"""问诊子图（系分 §5.2.1）。

调 query_consultations / query_prescriptions → interpret_prescription。
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


def build_consultation_graph():
    """构造问诊子图。"""
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
