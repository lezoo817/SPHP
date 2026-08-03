"""pgvector 向量库管理。

使用 langchain-postgres 的 PGVector 作为向量存储后端，
与 Java 端共用同一个 PostgreSQL 实例（连接参数由 .env 的 PG_* 提供）。
"""

import logging
from functools import lru_cache

from langchain_postgres import PGVector

from app.engine.rag.embedder import build_embedding
from app.infrastructure.config.settings import get_settings

logger = logging.getLogger(__name__)


@lru_cache
def _connection_string() -> str:
    """拼装 SQLAlchemy 连接串（psycopg3 驱动）。

    密码需 URL 编码，否则含 @ : / 等特殊字符时连接串解析失败。
    """
    from urllib.parse import quote_plus

    s = get_settings()
    return (
        f"postgresql+psycopg://{quote_plus(s.pg_user)}:{quote_plus(s.pg_password)}"
        f"@{s.pg_host}:{s.pg_port}/{s.pg_database}"
    )


@lru_cache
def get_vectorstore() -> PGVector:
    """获取全局唯一的 PGVector 实例（单例，避免重复建连接池）。

    首次调用时会自动建表（langchain_postgres 内部处理）。
    注意：连接库需已安装 pgvector 扩展（CREATE EXTENSION vector），
    否则首次调用建表时会报错——请确保目标 PG 实例上扩展已就绪。
    """
    return PGVector(
        embeddings=build_embedding(),
        collection_name=get_settings().kb_collection,
        connection=_connection_string(),
        use_jsonb=True,
        async_mode=True,
    )


async def close_vectorstore() -> None:
    """关闭 PGVector 异步引擎（优雅关闭时调用，释放连接池）。

    PGVector 内部基于 SQLAlchemy ``create_async_engine`` 建连接池，lifespan
    退出时不 dispose 会在热重载/重启时残留连接（P2 修复）。仅当实例已创建
    （cache 非空）时才执行关闭，避免为关闭而新建连接；最后清除 lru_cache，
    防止后续 get_vectorstore() 返回已释放的死引擎。

    Note:
        依赖 PGVector 私有属性 ``_async_engine``（langchain_postgres 内部），
        仅用于资源释放；若上游调整属性名，此处失败仅告警不阻断关闭。
    """
    if get_vectorstore.cache_info().currsize == 0:
        return
    try:
        engine = get_vectorstore()._async_engine
        if engine is not None:
            await engine.dispose()
        logger.info("Vectorstore async engine disposed")
    except Exception:
        logger.warning("关闭向量库连接失败（忽略）", exc_info=True)
    finally:
        get_vectorstore.cache_clear()
