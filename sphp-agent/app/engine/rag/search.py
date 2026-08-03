"""知识库检索：相似度搜索 + 结果格式化。

Agent 工具调用此模块完成 RAG 检索，将医疗知识作为上下文注入 prompt。
"""

import logging

from app.engine.rag.vectorstore import get_vectorstore
from app.infrastructure.config.settings import get_settings

logger = logging.getLogger(__name__)


async def search_knowledge(query: str, top_k: int | None = None) -> list[dict]:
    """根据自然语言 query 检索最相关的知识片段。

    Args:
        query: 用户问题或检索关键词。
        top_k: 返回条数，默认取 settings.kb_top_k。

    Returns:
        [{"content": "...", "source": "...", "score": 0.87}, ...]
        按相关度降序排列，已过滤低于 ``kb_min_score`` 的低质量结果。
        检索失败（embedding / 连接异常）时降级返回空列表，不抛异常，
        由上游节点 / 端点决定如何兜底。

    Raises:
        无：异常在内部捕获并记录日志。
    """
    try:
        k = top_k or get_settings().kb_top_k
        min_score = get_settings().kb_min_score
        store = get_vectorstore()

        results = await store.asimilarity_search_with_relevance_scores(query, k=k)

        formatted = []
        for doc, score in results:
            # 余弦分数截断到 [0, 1]（bge-m3 未 unit-norm 时可能略越界）
            normalized = max(0.0, min(1.0, round(float(score), 4)))
            if normalized < min_score:
                continue
            formatted.append(
                {
                    "content": doc.page_content,
                    "source": doc.metadata.get("source_file", doc.metadata.get("source", "未知")),
                    "score": normalized,
                }
            )

        logger.info("知识检索: query=%r, 命中 %d 条", query, len(formatted))
        return formatted
    except Exception as e:
        # 知识库不可用不应阻塞对话：降级为空列表，由上游兜底
        logger.warning("知识检索失败，降级为空: %s", e)
        return []


def format_context(results: list[dict]) -> str:
    """将检索结果拼成可直接塞进 prompt 的上下文字符串。"""
    if not results:
        return "（未检索到相关知识）"

    lines = []
    for i, r in enumerate(results, 1):
        lines.append(f"[{i}] (来源: {r['source']}, 相关度: {r['score']})\n{r['content']}")
    return "\n\n".join(lines)
