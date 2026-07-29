"""知识库检索：相似度搜索 + 结果格式化。

Agent 工具调用此模块完成 RAG 检索，将医疗知识作为上下文注入 prompt。
"""

import logging

from app.infrastructure.config.settings import get_settings
from app.engine.rag.vectorstore import get_vectorstore

logger = logging.getLogger(__name__)


def search_knowledge(query: str, top_k: int | None = None) -> list[dict]:
    """根据自然语言 query 检索最相关的知识片段。

    Args:
        query: 用户问题或检索关键词。
        top_k: 返回条数，默认取 settings.kb_top_k。

    Returns:
        [{"content": "...", "source": "...", "score": 0.87}, ...]
        按相似度降序排列。
    """
    k = top_k or get_settings().kb_top_k
    store = get_vectorstore()

    results = store.similarity_search_with_relevance_scores(query, k=k)

    formatted = []
    for doc, score in results:
        formatted.append({
            "content": doc.page_content,
            "source": doc.metadata.get("source_file", doc.metadata.get("source", "未知")),
            "score": round(score, 4),
        })

    logger.info("知识检索: query=%r, 命中 %d 条", query, len(formatted))
    return formatted


def format_context(results: list[dict]) -> str:
    """将检索结果拼成可直接塞进 prompt 的上下文字符串。"""
    if not results:
        return "（未检索到相关知识）"

    lines = []
    for i, r in enumerate(results, 1):
        lines.append(f"[{i}] (来源: {r['source']}, 相关度: {r['score']})\n{r['content']}")
    return "\n\n".join(lines)
