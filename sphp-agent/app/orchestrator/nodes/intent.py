"""意图识别节点（系分 §5.2.2）。

使用 LLM 分类（非规则匹配），通过构造分类 prompt 让 LLM 从预定义标签中选择。
"""

from app.orchestrator.state import AgentState
from app.engine.llm.factory import build_llm


async def intent_node(state: AgentState) -> dict:
    """分类用户意图，返回 {"intent": "triage"}。

    LLM 返回非预定义标签时默认归类为 qa（RAG 兜底回答）。
    消息 ≤10 字且命中关键词时跳过 LLM 调用，直接路由，减少首字延迟。
    """
    # 关键词快速通道
    user_message = ""
    if state.get("messages"):
        last_msg = state["messages"][-1]
        user_message = last_msg.content if hasattr(last_msg, "content") else str(last_msg)

    if len(user_message) <= 10:
        if any(kw in user_message for kw in ["挂号", "预约", "号源", "排班"]):
            return {"intent": "registration"}
        if any(kw in user_message for kw in ["买药", "购药", "下单", "配送"]):
            return {"intent": "pharmacy"}

    # TODO: LLM 分类
    # 构造分类 prompt → 调 LLM → 解析意图标签
    return {"intent": "qa"}  # 兜底
