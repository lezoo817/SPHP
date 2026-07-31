"""主图构造（系分 §5.2.3）。

LangGraph StateGraph 串联 7 个标准节点，主图负责鉴权、意图路由和安全校验。
业务操作委托给 4 个子图处理。
"""

from langgraph.graph import StateGraph, END
from langgraph.checkpoint.memory import MemorySaver

from app.orchestrator.state import AgentState
from app.orchestrator.nodes.auth import auth_node
from app.orchestrator.nodes.intent import intent_node
from app.orchestrator.nodes.rag import rag_node
from app.orchestrator.nodes.reply import reply_node


def route_by_intent(state: AgentState) -> str:
    """读 state.intent → 返回目标节点名。"""
    intent = state.get("intent", "qa")
    routing = {
        "triage": "triage_graph",
        "registration": "registration_graph",
        "consultation": "consultation_graph",
        "pharmacy": "pharmacy_graph",
        "qa": "qa_node",
        "chitchat": "chitchat_node",
    }
    return routing.get(intent, "qa_node")


def build_main_graph():
    """构造主图。"""
    builder = StateGraph(AgentState)

    # 注册主图节点
    builder.add_node("auth_node", auth_node)
    builder.add_node("intent_node", intent_node)
    builder.add_node("qa_node", rag_node)
    builder.add_node("reply_node", reply_node)

    # TODO: 注册子图（编译后的 CompiledGraph）
    # builder.add_node("triage_graph", triage_graph.compile())
    # builder.add_node("registration_graph", registration_graph.compile())
    # builder.add_node("consultation_graph", consultation_graph.compile())
    # builder.add_node("pharmacy_graph", pharmacy_graph.compile())

    # 入口
    builder.set_entry_point("auth_node")

    # 条件边：意图路由
    builder.add_conditional_edges(
        "intent_node",
        route_by_intent,
        {
            "triage": "triage_graph",
            "registration": "registration_graph",
            "consultation": "consultation_graph",
            "pharmacy": "pharmacy_graph",
            "qa": "qa_node",
            "chitchat": "chitchat_node",
        },
    )

    # 所有业务节点汇聚到 reply_node
    builder.add_edge("qa_node", "reply_node")
    # builder.add_edge("triage_graph", "reply_node")
    # builder.add_edge("registration_graph", "reply_node")
    # builder.add_edge("consultation_graph", "reply_node")
    # builder.add_edge("pharmacy_graph", "reply_node")

    builder.add_edge("reply_node", END)

    # 编译（开发环境用 MemorySaver，生产换 PostgresSaver）
    graph = builder.compile(checkpointer=MemorySaver())
    return graph
