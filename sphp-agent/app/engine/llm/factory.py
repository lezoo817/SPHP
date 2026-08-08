"""LLM 工厂（系分 §4.4 引擎层）。

通过 OpenAI 兼容接口实例化国内模型（DeepSeek / 智谱 / 通义）。
主供应商 API key 缺失时，按固定顺序降级到首个已配置 key 的备用供应商
（M6-B2 降级策略）。运行时 API 故障由各节点 try/except 兜底降级回复，
本工厂保证始终返回可用的 ChatOpenAI 实例（保留 LangGraph token 流式）。

Embedding 工厂已移至 app.engine.rag.embedder。
"""

import logging

from langchain_openai import ChatOpenAI
from pydantic import SecretStr

from app.infrastructure.config.settings import Settings, get_settings

logger = logging.getLogger(__name__)

# 备用供应商降级顺序（主供应商不可用时按此顺序尝试）
_FALLBACK_ORDER = ["deepseek", "zhipu", "qwen"]


def build_llm(provider: str | None = None, temperature: float | None = None) -> ChatOpenAI:
    """构造一个 ChatOpenAI 实例（M6-B2 支持供应商降级）。

    Args:
        provider: deepseek / zhipu / qwen，留空用 settings.llm_provider。
            作为主供应商优先；其 API key 缺失时降级到首个已配置 key 的备用供应商。
        temperature: 温度，留空用 settings.llm_temperature。

    Returns:
        ChatOpenAI: 首个可用供应商的实例。

    Raises:
        ValueError: 全部供应商均未配置 API key 时抛出。
    """
    settings = get_settings()
    temperature = temperature if temperature is not None else settings.llm_temperature
    if temperature is None:
        raise ValueError("未配置 LLM_TEMPERATURE，请在 .env 设置（如 0.3）")
    api_key, base_url, model = _resolve_llm_config(settings, provider or settings.llm_provider)

    return ChatOpenAI(
        model=model,
        api_key=SecretStr(api_key),
        base_url=base_url,
        temperature=temperature,
        streaming=True,
        stream_chunk_timeout=300.0,
        # 请求超时：字段名为 request_timeout，pydantic 生成的 __init__ 签名按别名 timeout 接受
        timeout=300.0,
    )


def _resolve_llm_config(settings: Settings, primary: str) -> tuple[str, str, str]:
    """按优先级解析可用供应商的 (api_key, base_url, model)。

    主供应商优先，其 API key 缺失时按 ``_FALLBACK_ORDER`` 降级到首个
    已配置 key 的供应商；全部缺失时抛 ValueError（含检查过的供应商列表）。

    Args:
        settings: 应用配置（含各供应商 *_api_key / *_base_url / *_model）。
        primary: 主供应商名（deepseek / zhipu / qwen）。

    Returns:
        tuple[str, str, str]: (api_key, base_url, model)。

    Raises:
        ValueError: 全部供应商均未配置 API key。
    """
    chain = [primary] + [p for p in _FALLBACK_ORDER if p != primary]

    for p in chain:
        api_key, base_url, model = settings.llm_config(p)
        if api_key:
            if p != primary:
                logger.warning("LLM 主供应商 %s 未配置 API key，降级到 %s", primary, p)
            return api_key, base_url, model

    raise ValueError(f"未配置任何 LLM 供应商的 API key（已检查: {', '.join(chain)}）")
