"""主图构造（系分 §5.2.3）。

LangGraph StateGraph 串联标准节点：auth -> [B 端直达工具子图 | intent ->
业务子图/qa/chitchat] -> reply。

M8-3 B 端意图路由：scope=b_end 跳过 C 端意图分类、auth_node 后直接进入
B 端全量工具子图（省一次 LLM 意图分类调用 + 避免 C 端 6 类意图语义吞掉
B 端工具）。C 端保持 auth -> intent -> 意图路由 -> 业务子图的原有链路。

M8-2 健康档案子图：C 端意图新增 health（健康档案场景五），路由到
health_graph 直达 10 工具白名单子图，补齐此前无 intent 路由的缺口。
"""

from typing import Any

from langgraph.graph import END, StateGraph

from app.orchestrator.checkpointer import build_checkpointer
from app.orchestrator.graphs._common import build_tool_subgraph
from app.orchestrator.graphs.consult_graph import build_consultation_graph
from app.orchestrator.graphs.health_graph import build_health_graph
from app.orchestrator.graphs.pharmacy_graph import build_pharmacy_graph
from app.orchestrator.graphs.registration_graph import build_registration_graph
from app.orchestrator.graphs.triage_graph import build_triage_graph
from app.orchestrator.nodes.auth import auth_node
from app.orchestrator.nodes.chitchat import chitchat_node
from app.orchestrator.nodes.intent import intent_node
from app.orchestrator.nodes.preset import PRESET_INTERPRET_PRESCRIPTION, preset_action_node
from app.orchestrator.nodes.rag import rag_node
from app.orchestrator.nodes.reply import reply_node
from app.orchestrator.nodes.tool_executor import tool_executor
from app.orchestrator.state import AgentState


def route_by_scope(state: AgentState) -> str:
    """auth_node 后按服务端分流（M8-3）。

    B 端（scope=b_end）跳过 C 端意图分类，直达 B 端全量工具子图——
    B 端工具与 C 端 6 类意图语义无关（医生问诊/处方审核/报告解读均为 B 端
    专属流程），走意图分类大概率误归到 qa 分支而吞掉全部 9 个工具
    （qa 分支只检索知识库不调工具）。直达同时省一次 LLM 意图分类调用。

    Args:
        state: 当前图状态，含 scope 字段。

    Returns:
        str: "b_end_tool_graph"（B 端）或 "intent_node"（C 端及未知）。
    """
    return "b_end_tool_graph" if state.get("scope") == "b_end" else "intent_node"


def route_after_auth(state: AgentState) -> str:
    """鉴权后优先路由受控预设动作。

    Args:
        state: 已完成鉴权的 Agent 状态。

    Returns:
        预设动作节点或既有按服务端分流的目标名称。
    """
    prescription_id = state.get("preset_prescription_id")
    if (
        state.get("scope") == "c_end"
        and state.get("preset_action") == PRESET_INTERPRET_PRESCRIPTION
        and isinstance(prescription_id, int)
        and not isinstance(prescription_id, bool)
        and prescription_id > 0
    ):
        return "preset_action_node"
    return route_by_scope(state)


def route_by_intent(state: AgentState) -> str:
    """读 state.intent → 返回目标节点名（仅 C 端调用，M8-3）。

    业务意图路由到对应工具子图，qa 路由到 rag_node（知识检索），
    chitchat 路由到 chitchat_node（不检索，仅日常回复）。
    """
    intent = state.get("intent") or "qa"
    routing = {
        "triage": "triage_graph",
        "registration": "registration_graph",
        "consultation": "consultation_graph",
        "pharmacy": "pharmacy_graph",
        "health": "health_graph",  # M8-2 健康档案场景五
        "qa": "qa_node",
        "chitchat": "chitchat_node",
    }
    return routing.get(intent, "qa_node")


def build_main_graph() -> Any:
    """构造主图。"""
    builder = StateGraph(AgentState)

    # 注册主图节点
    builder.add_node("auth_node", auth_node)
    builder.add_node("intent_node", intent_node)
    builder.add_node("qa_node", rag_node)
    builder.add_node("chitchat_node", chitchat_node)
    builder.add_node("preset_action_node", preset_action_node)
    builder.add_node("preset_tool_executor", tool_executor)
    builder.add_node("reply_node", reply_node)

    # 注册业务子图（编译后的 CompiledGraph）
    builder.add_node("triage_graph", build_triage_graph())
    builder.add_node("registration_graph", build_registration_graph())
    builder.add_node("consultation_graph", build_consultation_graph())
    builder.add_node("pharmacy_graph", build_pharmacy_graph())
    # M8-2 健康档案子图（场景五，10 工具白名单）
    builder.add_node("health_graph", build_health_graph())

    # M8-3 B 端直达工具子图：tool_names=None 绑定当前 scope（b_end）全量
    # L1/L2 工具（9 个），B 端无白名单约束（M5 定案，全量绑定）
    builder.add_node("b_end_tool_graph", build_tool_subgraph(tool_names=None))

    # 入口
    builder.set_entry_point("auth_node")

    # 鉴权后先处理受控预设；非预设请求维持原有按服务端分流逻辑。
    builder.add_conditional_edges(
        "auth_node",
        route_after_auth,
        {
            "preset_action_node": "preset_action_node",
            "b_end_tool_graph": "b_end_tool_graph",
            "intent_node": "intent_node",
        },
    )

    # 预设仅构造固定的 L1 处方解读调用，再复用标准执行器和回复节点。
    builder.add_edge("preset_action_node", "preset_tool_executor")
    builder.add_edge("preset_tool_executor", "reply_node")

    # 条件边：意图路由（仅 C 端执行）
    builder.add_conditional_edges(
        "intent_node",
        route_by_intent,
        {
            "triage_graph": "triage_graph",
            "registration_graph": "registration_graph",
            "consultation_graph": "consultation_graph",
            "pharmacy_graph": "pharmacy_graph",
            "health_graph": "health_graph",
            "qa_node": "qa_node",
            "chitchat_node": "chitchat_node",
        },
    )

    # 所有业务节点汇聚到 reply_node
    builder.add_edge("qa_node", "reply_node")
    builder.add_edge("chitchat_node", "reply_node")
    builder.add_edge("triage_graph", "reply_node")
    builder.add_edge("registration_graph", "reply_node")
    builder.add_edge("consultation_graph", "reply_node")
    builder.add_edge("pharmacy_graph", "reply_node")
    builder.add_edge("health_graph", "reply_node")
    builder.add_edge("b_end_tool_graph", "reply_node")

    builder.add_edge("reply_node", END)

    # 编译（M6-B3：按 settings.checkpointer_backend 选择，memory/postgres）
    graph = builder.compile(checkpointer=build_checkpointer())
    return graph
