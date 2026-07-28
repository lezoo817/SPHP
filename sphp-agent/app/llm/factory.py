"""LLM / Embedding 工厂：通过 OpenAI 兼容接口实例化国内模型。

三家供应商均兼容 OpenAI 接口：
- ChatOpenAI 用 model / api_key / base_url 接入对话模型
- OpenAIEmbeddings 用同样的方式接入向量化模型（当前使用智谱 embedding-3）
"""

from langchain_openai import ChatOpenAI, OpenAIEmbeddings

from app.config.settings import get_settings


def build_llm(provider: str | None = None, temperature: float = 0.3) -> ChatOpenAI:
    """构造一个 ChatOpenAI 实例。

    Args:
        provider: deepseek / zhipu / qwen，留空用 default_llm_provider。
        temperature: 温度，导诊等场景建议 0.2~0.4。
    """
    settings = get_settings()
    provider = provider or settings.default_llm_provider
    api_key, base_url, model = settings.llm_config(provider)

    if not api_key:
        raise ValueError(
            f"未配置 {provider} 的 API key，请在 .env 中填入对应 *_API_KEY"
        )

    return ChatOpenAI(
        model=model,
        api_key=api_key,
        base_url=base_url,
        temperature=temperature,
        streaming=True,
    )


def build_embedding() -> OpenAIEmbeddings:
    """构造向量化模型实例（智谱 embedding-3，OpenAI 兼容接口）。

    用于知识库文档切分后的向量化入库，以及检索时的 query 向量化。
    """
    settings = get_settings()

    if not settings.zhipu_api_key:
        raise ValueError(
            "未配置智谱 API key，请在 .env 中填入 ZHIPU_API_KEY（embedding 模型依赖智谱）"
        )

    return OpenAIEmbeddings(
        model=settings.zhipu_embedding_model,
        api_key=settings.zhipu_api_key,
        base_url=settings.zhipu_base_url,
    )
