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

    从知识库检索相关文档，注入到 LLM context。
    无检索结果时不注入上下文（避免把「未检索到」当系统知识传给 LLM）。
    """
    try:
        user_message = get_last_user_content(state)

        results = await search_knowledge(query=user_message)

        # 无命中：不注入上下文，避免误导 LLM
        if not results:
            return {"messages": []}

        context = format_context(results)
        return {"messages": [{"role": "system", "content": f"相关医学知识：\n{context}"}]}

    except Exception as e:
        # RAG 失败时不阻塞流程
        logger.warning("RAG 检索失败: %s", e)
        return {"messages": []}
