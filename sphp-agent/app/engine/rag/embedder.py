"""Embedding 模型工厂。

当前从 engine.llm.factory 中拆分出来，支持多供应商切换。
"""

from langchain_openai import OpenAIEmbeddings

from app.infrastructure.config.settings import get_settings


def build_embedding(provider: str | None = None) -> OpenAIEmbeddings:
    """构造 Embedding 模型实例（OpenAI 兼容接口）。

    Args:
        provider: 当前仅支持 zhipu（默认），后续扩展其他供应商。

    Returns:
        OpenAIEmbeddings 实例。
    """
    settings = get_settings()

    if not settings.zhipu_api_key:
        raise ValueError(
            "未配置智谱 API key，请在 .env 中填入 ZHIPU_API_KEY（embedding 模型依赖智谱）"
        )

    # TODO: 当其他供应商的 embedding 可用时，按 provider 参数分发
    return OpenAIEmbeddings(
        model=settings.zhipu_embedding_model,
        api_key=settings.zhipu_api_key,
        base_url=settings.zhipu_base_url,
    )
