"""主图构造（系分 §5.2.3）。

LangGraph StateGraph 串联标准节点：auth -> intent -> [业务子图 | qa] -> reply。
业务意图（triage/registration/consultation/pharmacy）路由到对应子图，
qa/chitchat 路由到 rag_node 检索回答。
"""

from langgraph.checkpoint.memory import MemorySaver
from langgraph.graph import END, StateGraph

from app.orchestrator.graphs.consult_graph import build_consultation_graph
from app.orchestrator.graphs.pharmacy_graph import build_pharmacy_graph
from app.orchestrator.graphs.registration_graph import build_registration_graph
from app.orchestrator.graphs.triage_graph import build_triage_graph
from app.orchestrator.nodes.auth import auth_node
from app.orchestrator.nodes.intent import intent_node
from app.orchestrator.nodes.rag import rag_node
from app.orchestrator.nodes.reply import reply_node
from app.orchestrator.state import AgentState


def route_by_intent(state: AgentState) -> str:
    """读 state.intent → 返回目标节点名。

    业务意图路由到对应工具子图，qa/chitchat 路由到 rag_node。
    """
    intent = state.get("intent", "qa")
    routing = {
        "triage": "triage_graph",
        "registration": "registration_graph",
        "consultation": "consultation_graph",
        "pharmacy": "pharmacy_graph",
        "qa": "qa_node",
        "chitchat": "qa_node",  # 待实现 chitchat_node，暂走 qa
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

    # 注册业务子图（编译后的 CompiledGraph）
    builder.add_node("triage_graph", build_triage_graph())
    builder.add_node("registration_graph", build_registration_graph())
    builder.add_node("consultation_graph", build_consultation_graph())
    builder.add_node("pharmacy_graph", build_pharmacy_graph())

    # 入口
    builder.set_entry_point("auth_node")

    # auth_node → intent_node
    builder.add_edge("auth_node", "intent_node")

    # 条件边：意图路由
    builder.add_conditional_edges(
        "intent_node",
        route_by_intent,
        {
            "triage_graph": "triage_graph",
            "registration_graph": "registration_graph",
            "consultation_graph": "consultation_graph",
            "pharmacy_graph": "pharmacy_graph",
            "qa_node": "qa_node",
        },
    )

    # 所有业务节点汇聚到 reply_node
    builder.add_edge("qa_node", "reply_node")
    builder.add_edge("triage_graph", "reply_node")
    builder.add_edge("registration_graph", "reply_node")
    builder.add_edge("consultation_graph", "reply_node")
    builder.add_edge("pharmacy_graph", "reply_node")

    builder.add_edge("reply_node", END)

    # 编译（开发环境用 MemorySaver，生产换 PostgresSaver）
    graph = builder.compile(checkpointer=MemorySaver())
    return graph
