"""知识库检索：相似度搜索 + 结果格式化。

Agent 工具调用此模块完成 RAG 检索，将医疗知识作为上下文注入 prompt。
"""

import logging
import math
from typing import Any

from app.engine.rag.vectorstore import get_vectorstore
from app.infrastructure.config.settings import get_settings

logger = logging.getLogger(__name__)


async def search_knowledge(
    query: str,
    top_k: int | None = None,
    category: str | None = None,
) -> list[dict[str, Any]]:
    """根据自然语言 query 检索最相关的知识片段。

    Args:
        query: 用户问题或检索关键词。
        top_k: 返回条数，默认取 settings.kb_top_k。
        category: 分类过滤（patient_edu / clinical_ref），不传则不过滤。

    Returns:
        [{"content": "...", "source": "...", "score": 0.87,
          "source_doc": "...", "source_page": "...", "chunk_id": "..."}, ...]
        按相关度降序排列，已过滤低于 ``kb_min_score`` 的低质量结果。
        source_doc / source_page / chunk_id 供系分 §6.5.2 检索接口展示来源引用。
        检索失败（embedding / 连接异常）时降级返回空列表，不抛异常，
        由上游节点 / 端点决定如何兜底。

    Raises:
        无：异常在内部捕获并记录日志。
    """
    try:
        k = top_k or get_settings().kb_top_k
        min_score = get_settings().kb_min_score
        store = get_vectorstore()

        kwargs: dict[str, Any] = {"k": k}
        if category:
            # PGVector 支持 LangChain 标准 filter（use_jsonb=True 走 JSONB 查询）
            kwargs["filter"] = {"category": category}
        results = await store.asimilarity_search_with_relevance_scores(query, **kwargs)

        formatted = []
        for doc, score in results:
            # P1-8：NaN 距离分数直接过滤。embedding 向量含 NaN 时 PGVector 返回
            # 的相似度可能为 NaN，而 ``min(1.0, float('nan'))`` 返回 1.0
            # （NaN 与任何数比较恒 False），NaN 会被钳制为 1.0 置顶污染检索
            # 结果，把无关片段当最相关知识注入 LLM。此处先判 NaN 再钳制。
            try:
                raw = float(score)
            except (TypeError, ValueError):
                continue
            if math.isnan(raw):
                logger.warning("知识检索跳过 NaN 距离分数（embedding 含 NaN）")
                continue
            # 余弦分数截断到 [0, 1]（bge-m3 未 unit-norm 时可能略越界）
            normalized = max(0.0, min(1.0, round(raw, 4)))
            if normalized < min_score:
                continue
            formatted.append(_format_result(doc, normalized))

        logger.info("知识检索: query=%r, category=%r, 命中 %d 条", query, category, len(formatted))
        return formatted
    except Exception as e:
        # 知识库不可用不应阻塞对话：降级为空列表，由上游兜底
        logger.warning("知识检索失败，降级为空: %s", e)
        return []


def _format_result(doc: Any, score: float) -> dict[str, Any]:
    """将单个检索结果格式化为系分 §6.5.2 字段结构。

    Args:
        doc: LangChain Document（metadata 含 title/category/source/source_page）。
        score: 归一化余弦相似度分数。

    Returns:
        dict: 含 content / source / score / source_doc / source_page / chunk_id。
        source 保留兼容内部节点消费（format_context 读取 source/score/content）。
    """
    metadata = doc.metadata or {}
    source_doc = metadata.get("title", metadata.get("source_file", "未知"))
    return {
        "content": doc.page_content,
        "source": metadata.get("source", source_doc),
        "score": score,
        "source_doc": source_doc,
        "source_page": metadata.get("source_page", ""),
        "chunk_id": doc.id or "",
    }


def format_context(results: list[dict[str, Any]]) -> str:
    """将检索结果拼成可直接塞进 prompt 的上下文字符串。"""
    if not results:
        return "（未检索到相关知识）"

    lines = []
    for i, r in enumerate(results, 1):
        lines.append(f"[{i}] (来源: {r['source']}, 相关度: {r['score']})\n{r['content']}")
    return "\n\n".join(lines)
