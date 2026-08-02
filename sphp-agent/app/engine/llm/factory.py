"""LLM 工厂（系分 §4.4 引擎层）。

通过 OpenAI 兼容接口实例化国内模型（DeepSeek / 智谱 / 通义）。
Embedding 工厂已移至 app.engine.rag.embedder。
"""

from langchain_openai import ChatOpenAI

from app.infrastructure.config.settings import get_settings


def build_llm(provider: str | None = None, temperature: float | None = None) -> ChatOpenAI:
    """构造一个 ChatOpenAI 实例。

    Args:
        provider: deepseek / zhipu / qwen，留空用 settings.llm_provider。
        temperature: 温度，留空用 settings.llm_temperature。
    """
    settings = get_settings()
    provider = provider or settings.llm_provider
    temperature = temperature if temperature is not None else settings.llm_temperature
    api_key, base_url, model = settings.llm_config(provider)

    if not api_key:
        raise ValueError(f"未配置 {provider} 的 API key，请在 .env 中填入对应 *_API_KEY")

    return ChatOpenAI(
        model=model,
        api_key=api_key,
        base_url=base_url,
        temperature=temperature,
        streaming=True,
    )
