"""集中读取 .env 配置（系分 §9.5）。

所有密钥只从环境变量读取，不硬编码。
通过 pydantic-settings 自动校验后加载为 Settings 单例。
"""

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Agent 全局配置（系分 §9.5）。"""

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    # ---- 应用 ----
    app_name: str = "智愈先锋 AI Agent 服务"
    debug: bool = False
    agent_host: str = "0.0.0.0"
    agent_port: int = 8081

    # ---- Java 后端（系分 §9.5）----
    java_base_url: str = "http://localhost:8080"
    c_auth_parse_path: str = "/api/c/v1/auth/token/parse"
    b_auth_parse_path: str = "/api/b/auth/token/parse"

    # ---- LLM 供应商 ----
    llm_provider: str = "zhipu"
    llm_api_key: str = ""
    llm_base_url: str | None = None
    llm_model: str | None = None
    llm_temperature: float = 0.3
    llm_max_tokens: int = 2048

    # ---- DeepSeek ----
    deepseek_api_key: str = ""
    deepseek_base_url: str = "https://api.deepseek.com"
    deepseek_model: str = "deepseek-chat"

    # ---- 智谱 GLM ----
    zhipu_api_key: str = ""
    zhipu_base_url: str = "https://open.bigmodel.cn/api/paas/v4"
    zhipu_model: str = "glm-4"

    # ---- 通义千问 ----
    dashscope_api_key: str = ""
    dashscope_base_url: str = "https://dashscope.aliyuncs.com/compatible-mode/v1"
    dashscope_model: str = "qwen-plus"

    # ---- Embedding 供应商 ----
    embedding_provider: str = "siliconflow"
    embedding_api_key: str = ""
    embedding_base_url: str | None = None
    embedding_model: str | None = None

    # ---- 硅基流动 SiliconFlow（Embedding 向量化，BAAI/bge-m3）----
    siliconflow_api_key: str = ""
    siliconflow_base_url: str = "https://api.siliconflow.cn/v1"
    siliconflow_embedding_model: str = "BAAI/bge-m3"

    # ---- PostgreSQL + pgvector ----
    pg_host: str = "localhost"
    pg_port: int = 5433  # 本地 docker 映射端口
    pg_user: str = "admin"
    pg_password: str = "sphp123"
    pg_database: str = "SPHP_pg"

    # ---- Redis ----
    redis_host: str = "localhost"
    redis_port: int = 6379
    redis_password: str | None = None

    # ---- RabbitMQ ----
    rabbitmq_url: str = "amqp://guest:guest@localhost:5672/"

    # ---- 知识库 ----
    kb_collection: str = "medical_knowledge"
    kb_chunk_size: int = 500
    kb_chunk_overlap: int = 50
    kb_top_k: int = 5

    # ---- Agent 行为参数 ----
    confirm_token_ttl: int = 300
    rate_limit_per_minute: int = 20
    memory_window_size: int = 10
    log_level: str = "INFO"
    mcp_transport: str = "stdio"

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
