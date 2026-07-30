"""RAG 检索节点（系分 §5.7）。

从医疗知识库检索相关内容，注入 LLM 上下文。
"""

from app.orchestrator.state import AgentState
from app.engine.rag.search import search_knowledge, format_context


async def rag_node(state: AgentState) -> dict:
    """从医疗知识库检索相关内容，返回相关知识片段列表。"""
    user_message = ""
    if state.get("messages"):
        last_msg = state["messages"][-1]
        user_message = last_msg.content if hasattr(last_msg, "content") else str(last_msg)

    results = search_knowledge(query=user_message)
    context = format_context(results)

    # TODO: 将 context 注入 messages 作为系统上下文
    return {"tool_results": [{"rag_context": context}]}
