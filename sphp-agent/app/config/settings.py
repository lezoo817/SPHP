"""集中读取 .env 配置。所有密钥只从环境变量读取，不硬编码。"""

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    # ---- 应用 ----
    app_name: str = "智愈先锋 AI Agent 服务"
    debug: bool = False
    host: str = "0.0.0.0"
    port: int = 8000

    # ---- 后端服务地址 ----
    b_end_base_url: str = "http://localhost:8081"   # B 端后端（医院管理/医生工作台）
    c_end_base_url: str = "http://localhost:8082"   # C 端后端（患者挂号/问诊/购药）

    # ---- LLM 供应商 ----
    default_llm_provider: str = "deepseek"

    # ---- DeepSeek ----
    deepseek_api_key: str = ""
    deepseek_base_url: str = "https://api.deepseek.com"
    deepseek_model: str = "deepseek-chat"

    # ---- 智谱 GLM ----
    zhipu_api_key: str = ""
    zhipu_base_url: str = "https://open.bigmodel.cn/api/paas/v4"
    zhipu_model: str = "glm-4.6v"

    # ---- 通义千问 ----
    dashscope_api_key: str = ""
    dashscope_base_url: str = "https://dashscope.aliyuncs.com/compatible-mode/v1"
    dashscope_model: str = "qwen-plus"

    # ---- LangSmith（可选追踪）----
    langsmith_tracing: bool = False
    langsmith_api_key: str = ""
    langsmith_endpoint: str = "https://api.smith.langchain.com"

    def llm_config(self, provider: str) -> tuple[str, str, str]:
        """返回 (api_key, base_url, model)。"""
        configs = {
            "deepseek": (self.deepseek_api_key, self.deepseek_base_url, self.deepseek_model),
            "zhipu": (self.zhipu_api_key, self.zhipu_base_url, self.zhipu_model),
            "qwen": (self.dashscope_api_key, self.dashscope_base_url, self.dashscope_model),
        }
        if provider not in configs:
            raise ValueError(f"未知的 LLM 供应商: {provider}")
        return configs[provider]


@lru_cache
def get_settings() -> Settings:
    """单例配置，避免重复读 .env。"""
    return Settings()
