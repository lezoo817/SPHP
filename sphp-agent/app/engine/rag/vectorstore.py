"""pgvector 向量库管理。

使用 langchain-postgres 的 PGVector 作为向量存储后端，
与 Java 端共用同一个 PostgreSQL 实例（连接参数由 .env 的 PG_* 提供）。
"""

from functools import lru_cache

from langchain_postgres import PGVector

from app.infrastructure.config.settings import get_settings
from app.engine.rag.embedder import build_embedding


@lru_cache
def _connection_string() -> str:
    """拼装 SQLAlchemy 连接串（psycopg3 驱动）。"""
    s = get_settings()
    return (
        f"postgresql+psycopg://{s.pg_user}:{s.pg_password}"
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
    )
