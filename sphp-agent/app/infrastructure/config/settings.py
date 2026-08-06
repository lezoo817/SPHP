"""集中读取 .env 配置（系分 §9.5）。

所有密钥只从环境变量读取，不硬编码。
通过 pydantic-settings 自动校验后加载为 Settings 单例。

.env 路径基于本文件位置解析（相对路径在 CWD 不一致时会导致读不到），
因此使用绝对路径：sphp-agent/.env，保证任意工作目录下都能读取。
"""

import logging
from functools import lru_cache
from pathlib import Path

from pydantic import model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

logger = logging.getLogger(__name__)

# sphp-agent/.env（本文件位于 app/infrastructure/config/，向上 4 级）
_ENV_FILE = Path(__file__).resolve().parents[3] / ".env"


class Settings(BaseSettings):
    """Agent 全局配置（系分 §9.5）。"""

    model_config = SettingsConfigDict(
        env_file=_ENV_FILE,
        env_file_encoding="utf-8",
        extra="ignore",
    )

    # ---- 应用 ----
    app_name: str = "智愈先锋 AI Agent 服务"
    # 应用版本号（供 /health 与 OpenAPI 引用，避免多处理硬编码）
    app_version: str = "2.0.0"
    debug: bool = False
    # 无 token 时降级为匿名（仅开发环境设为 true，生产必须 false）
    allow_anonymous: bool = False
    # 监听地址：默认回环（P3-5 安全默认，避免开发环境误暴露到局域网）；
    # 生产部署需在 .env 覆盖为 0.0.0.0 供外部访问
    agent_host: str = "127.0.0.1"
    agent_port: int = 8081
    # CORS 允许的前端来源（生产由 .env 的 CORS_ORIGINS 覆盖）
    # B 端前端(b-sphp, max dev 默认 8000)、C 端前端(user-h5, PORT=8001)
    cors_origins: list[str] = ["http://localhost:8000", "http://localhost:8001"]

    # ---- Java 后端（系分 §9.5）----
    java_base_url: str = "http://localhost:8080"
    c_auth_parse_path: str = "/api/c/v1/auth/token/parse"
    b_auth_parse_path: str = "/api/b/auth/token/parse"

    # ---- LLM 供应商（占位符，实际值从 .env 读取）----
    # LLM_PROVIDER / LLM_TEMPERATURE / LLM_MODEL 在 .env 配置，切换只改 env 不改源码
    llm_provider: str = ""
    llm_temperature: float | None = None
    # 默认模型：配则覆盖各供应商特定 *_model（如 zhipu_model），切换模型只改 .env
    llm_model: str = ""

    # ---- DeepSeek ----
    deepseek_api_key: str = ""
    deepseek_base_url: str = "https://api.deepseek.com"
    deepseek_model: str = "deepseek-chat"

    # ---- 智谱 GLM ----
    zhipu_api_key: str = ""
    zhipu_base_url: str = "https://open.bigmodel.cn/api/paas/v4"
    zhipu_model: str = "glm-4"
    zhipu_embedding_model: str = "embedding-3"

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

    # ---- 阿里云百炼 DashScope（Embedding 向量化，text-embedding-v4）----
    dashscope_embedding_api_key: str = ""
    dashscope_embedding_base_url: str = "https://dashscope.aliyuncs.com/compatible-mode/v1"
    dashscope_embedding_model: str = "text-embedding-v4"

    # ---- PostgreSQL + pgvector ----
    # 本地开发库密码请在 .env 的 PG_PASSWORD 配置，不硬编码到源码。
    pg_host: str = "localhost"
    pg_port: int = 5432
    pg_user: str = "sphp"
    pg_password: str = ""
    pg_database: str = "sphp"

    # ---- Redis ----
    # 从 .env 的 REDIS_HOST / REDIS_PORT / REDIS_PASSWORD 读取。
    redis_host: str = "localhost"
    redis_port: int = 6379
    redis_password: str | None = None

    # ---- 知识库 ----
    kb_collection: str = "medical_knowledge"
    kb_chunk_size: int = 500
    kb_chunk_overlap: int = 50
    kb_top_k: int = 5
    # 检索结果相关度阈值：低于该值的结果不返回（cosine score 0~1，越高越相关）
    kb_min_score: float = 0.3

    # ---- Agent 行为参数 ----
    # 会话 checkpointer 后端：memory（开发/测试，进程内存）/ postgres（生产，PG 持久化）
    checkpointer_backend: str = "memory"
    confirm_token_ttl: int = 300
    # 已确认操作回执 TTL（M5-T4 / T-M3-L1）：confirm 成功后写 Redis，
    # 下一轮对话一次性消费注入上下文，超时未消费则过期
    confirm_done_ttl: int = 3600
    rate_limit_per_minute: int = 20
    memory_window_size: int = 10
    # 工具结果注入 LLM 的最大字符数（reply._format_tool_results 截断，防上下文膨胀）
    max_data_chars: int = 2000
    # 子图工具调用最大迭代次数（graphs._common.route_continue，防 LLM 无限循环）
    max_tool_iterations: int = 5
    log_level: str = "INFO"

    def llm_config(self, provider: str) -> tuple[str, str, str]:
        """返回 (api_key, base_url, model)。

        model 优先用统一 ``LLM_MODEL`` 覆盖供应商特定 ``*_model``（如 zhipu_model），
        便于切换模型只改 .env 而不关心当前供应商；未配 ``LLM_MODEL`` 时回落到
        供应商特定 model。
        """
        configs = {
            "deepseek": (self.deepseek_api_key, self.deepseek_base_url, self.deepseek_model),
            "zhipu": (self.zhipu_api_key, self.zhipu_base_url, self.zhipu_model),
            "qwen": (self.dashscope_api_key, self.dashscope_base_url, self.dashscope_model),
        }
        if provider not in configs:
            raise ValueError(f"未知的 LLM 供应商: {provider}")
        api_key, base_url, model = configs[provider]
        # 统一 LLM_MODEL 优先：覆盖供应商特定 model，切换模型只改 .env 的 LLM_MODEL
        if self.llm_model:
            model = self.llm_model
        return api_key, base_url, model

    @model_validator(mode="after")
    def _enforce_safe_config(self) -> "Settings":
        """安全（P1-10）：DEBUG 与 ALLOW_ANONYMOUS 不可同时开启。

        双开会让服务完全无鉴权（DEBUG 跳过 knowledge 写鉴权 + 匿名放行全部
        端点），生产环境误配即裸奔。检测到双开时强制关闭匿名（降级），
        并记录 ERROR 警示；开发环境需匿名时请保持 DEBUG=False。
        """
        if self.debug and self.allow_anonymous:
            logger.error(
                "安全配置冲突：DEBUG=True 与 ALLOW_ANONYMOUS=true 同时开启"
                "（将跳过全部鉴权），已强制 ALLOW_ANONYMOUS=false 降级。"
                "生产环境必须 DEBUG=False / ALLOW_ANONYMOUS=false。"
            )
            self.allow_anonymous = False
        return self


@lru_cache
def get_settings() -> Settings:
    """单例配置，避免重复读 .env。"""
    return Settings()
