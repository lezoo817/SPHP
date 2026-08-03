"""会话 Checkpointer 工厂（M6-B3 生产持久化）。

按 ``settings.checkpointer_backend`` 选择 LangGraph 会话持久化后端：
- ``memory``：MemorySaver（开发/测试默认，进程内存，多 worker 不共享会话）
- ``postgres``：AsyncPostgresSaver（生产，PG 持久化，多 worker 共享会话）

postgres 为 lazy import：依赖 ``langgraph-checkpoint-postgres``（pyproject prod
extra），开发环境未安装时仅在使用 postgres 后端时才报错，不影响 memory 路径。
"""

import logging

from app.infrastructure.config.settings import get_settings

logger = logging.getLogger(__name__)

# Postgres 连接池全局单例（复用于所有 AsyncPostgresSaver 实例）
_pg_pool = None


def build_checkpointer():
    """返回会话 checkpointer（按 backend 配置，M6-B3）。

    Returns:
        MemorySaver 或 AsyncPostgresSaver。

    Raises:
        ValueError: backend 非 memory/postgres。
        RuntimeError: postgres 后端但依赖未安装。
    """
    settings = get_settings()
    backend = settings.checkpointer_backend

    if backend == "memory":
        from langgraph.checkpoint.memory import MemorySaver

        # 每次返回新实例：多个编译图互不共享内存会话（与历史行为一致）
        return MemorySaver()

    if backend == "postgres":
        return _build_postgres_saver()

    raise ValueError(f"未知的 checkpointer_backend: {backend}（可选 memory/postgres）")


def _build_postgres_saver():
    """构造 AsyncPostgresSaver（共享连接池，M6-B3）。"""
    global _pg_pool

    try:
        from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver
        from psycopg.rows import dict_row
        from psycopg_pool import AsyncConnectionPool
    except ImportError as e:
        raise RuntimeError(
            "checkpointer_backend=postgres 需要安装 langgraph-checkpoint-postgres"
            "（pyproject prod extra：pip install .[prod]）"
        ) from e

    settings = get_settings()
    conninfo = (
        f"host={settings.pg_host} port={settings.pg_port} "
        f"user={settings.pg_user} password={settings.pg_password} "
        f"dbname={settings.pg_database}"
    )

    if _pg_pool is None:
        _pg_pool = AsyncConnectionPool(
            conninfo,
            max_size=20,
            kwargs={"autocommit": True, "row_factory": dict_row, "prepare_threshold": 0},
        )
        logger.info(
            "Postgres checkpointer 连接池已创建: %s:%s/%s",
            settings.pg_host,
            settings.pg_port,
            settings.pg_database,
        )

    return AsyncPostgresSaver(_pg_pool)


async def setup_checkpointer() -> None:
    """初始化 checkpointer（postgres 时建表），在应用 lifespan 启动时调用。"""
    if get_settings().checkpointer_backend != "postgres":
        return
    saver = build_checkpointer()
    await saver.setup()
    logger.info("Postgres checkpointer 表已就绪")


async def close_checkpointer() -> None:
    """关闭 checkpointer（postgres 时关连接池），在应用关闭时调用。"""
    global _pg_pool
    if _pg_pool is not None:
        await _pg_pool.close()
        _pg_pool = None
        logger.info("Postgres checkpointer 连接池已关闭")
