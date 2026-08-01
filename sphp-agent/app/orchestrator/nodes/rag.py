"""RAG 检索节点（系分 §5.7）。

从医疗知识库检索相关内容，注入 LLM 上下文。
"""

from app.orchestrator.state import AgentState
from app.engine.rag.search import search_knowledge, format_context


async def rag_node(state: AgentState) -> dict:
    """从医疗知识库检索相关内容（系分 §5.7）。

    从知识库检索相关文档，注入到 LLM context。
    """
    try:
        from app.engine.rag.search import search_knowledge, format_context

        user_message = ""
        if state.get("messages"):
            last_msg = state["messages"][-1]
            user_message = last_msg.content if hasattr(last_msg, "content") else str(last_msg)

        # 调用RAG检索
        results = search_knowledge(query=user_message)
        context = format_context(results)

        # 将context注入messages作为系统上下文
        if context:
            return {"messages": [{"role": "system", "content": f"相关医学知识：\n{context}"}]}

        return {"messages": []}

    except Exception as e:
        # RAG失败时不阻塞流程
        import logging
        logging.getLogger(__name__).warning(f"RAG检索失败: {e}")
        return {"messages": []}
