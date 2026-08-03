"""RAG 检索节点（系分 §5.7）。

从医疗知识库检索相关内容，注入 LLM 上下文。
"""

import logging

from app.engine.rag.search import format_context, search_knowledge
from app.orchestrator.state import AgentState
from app.orchestrator.utils import get_last_user_content

logger = logging.getLogger(__name__)


async def rag_node(state: AgentState) -> dict:
    """从医疗知识库检索相关内容（系分 §5.7）。

    从知识库检索相关文档，写入 ``rag_context`` 供 reply_node 本次注入。
    不追加进 ``messages``：避免检索到的知识随 checkpoint 永久残留，
    跨轮次误导 LLM。无检索结果时不注入上下文。

    Returns:
        dict: 含 rag_context（检索结果文本）或空 dict（无命中 / 失败）。
    """
    try:
        user_message = get_last_user_content(state)

        results = await search_knowledge(query=user_message)

        # 无命中：显式清空 rag_context，避免跨轮残留
        if not results:
            return {"rag_context": None}

        context = format_context(results)
        return {"rag_context": f"相关医学知识：\n{context}"}

    except Exception as e:
        # RAG 失败时不阻塞流程，显式清空 rag_context
        logger.warning("RAG 检索失败: %s", e)
        return {"rag_context": None}
