"""Embedding 模型工厂（系分 §5.7 / §4.4 引擎层 rag/）。

支持多供应商切换（智谱 / 硅基流动 / 阿里云百炼 / OpenAI），通过 .env 配置 EMBEDDING_PROVIDER。
本地环境使用阿里云百炼 text-embedding-v4（维度 1024）。
"""

from langchain_openai import OpenAIEmbeddings

from app.infrastructure.config.settings import get_settings


def build_embedding() -> OpenAIEmbeddings:
    """构造 Embedding 模型实例（OpenAI 兼容接口）。

    根据 settings.embedding_provider 选择供应商：
    - dashscope: 阿里云百炼 text-embedding-v4（维度 1024，本地环境默认）
    - siliconflow: 硅基流动 BAAI/bge-m3（维度 1024）
    - zhipu: 智谱 embedding-3（维度 1024）
    - openai: OpenAI text-embedding-3-small

    Returns:
        OpenAIEmbeddings 实例。

    Raises:
        ValueError: 未配置对应供应商的 API key。
    """
    settings = get_settings()
    provider = settings.embedding_provider

    if provider == "dashscope":
        if not settings.dashscope_embedding_api_key:
            raise ValueError(
                "未配置阿里云百炼 API key，请在 .env 中填入 DASHSCOPE_EMBEDDING_API_KEY"
            )
        # 阿里云百炼 embedding 不接受 tiktoken 的 token ID 输入（会报 input.contents
        # 缺失），必须禁用长度检查，让 langchain 直接传原始文本。
        return OpenAIEmbeddings(
            model=settings.dashscope_embedding_model,
            api_key=settings.dashscope_embedding_api_key,
            base_url=settings.dashscope_embedding_base_url,
            check_embedding_ctx_length=False,
        )

    if provider == "siliconflow":
        if not settings.siliconflow_api_key:
            raise ValueError("未配置硅基流动 API key，请在 .env 中填入 SILICONFLOW_API_KEY")
        return OpenAIEmbeddings(
            model=settings.siliconflow_embedding_model,
            api_key=settings.siliconflow_api_key,
            base_url=settings.siliconflow_base_url,
        )

    if provider == "zhipu":
        if not settings.zhipu_api_key:
            raise ValueError("未配置智谱 API key，请在 .env 中填入 ZHIPU_API_KEY")
        return OpenAIEmbeddings(
            model=settings.zhipu_embedding_model,
            api_key=settings.zhipu_api_key,
            base_url=settings.zhipu_base_url,
        )

    if provider == "openai":
        if not settings.embedding_api_key:
            raise ValueError("未配置 Embedding API key，请在 .env 中填入 EMBEDDING_API_KEY")
        return OpenAIEmbeddings(
            model=settings.embedding_model or "text-embedding-3-small",
            api_key=settings.embedding_api_key,
            base_url=settings.embedding_base_url,
        )

    raise ValueError(f"未知的 Embedding 供应商: {provider}")
