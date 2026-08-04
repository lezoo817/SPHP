"""会话元数据存储层（历史会话列表）。

历史会话列表（``GET /api/chat/sessions``）的数据来源。按
``settings.checkpointer_backend`` 选择实现，**生命周期与 checkpointer 一致**：

- ``memory``：MemorySessionStore（进程内存 dict，与 MemorySaver 同生命周期，
  进程重启即失——开发已知限制）
- ``postgres``：PostgresSessionStore（PG ``agent_sessions`` 表，长期持久化，
  生产历史会话可回溯）

会话元数据与 LangGraph checkpoint（完整消息历史）**互补**：checkpoint 负责
"点开某会话恢复上下文续聊"（thread_id 机制已就绪），本表负责"列出用户有哪些
历史会话"（标题/最后消息/消息数/时间）。二者后端同源（checkpointer_backend），
避免"列表能看到但点开无消息"的脱节。

标题定稿策略：首次落库（新 session）写入首条用户消息截断标题，后续轮次 upsert
**不覆盖** title（首条消息定标题）。
"""

from __future__ import annotations

import logging
from datetime import UTC, datetime
from typing import TYPE_CHECKING, Any, Protocol, TypedDict

from app.infrastructure.config.settings import get_settings

if TYPE_CHECKING:
    from psycopg import AsyncConnection
    from psycopg_pool import AsyncConnectionPool

logger = logging.getLogger(__name__)

# 历史会话列表条目的最大返回数（量大后再加分页/游标）
SESSION_LIST_LIMIT = 50


class SessionRecord(TypedDict):
    """历史会话列表条目（对外返回字段）。"""

    session_id: str
    title: str
    last_message: str | None
    message_count: int
    updated_at: str  # ISO 8601 时间


class SessionStore(Protocol):
    """会话元数据存储接口。

    与 checkpointer 同生命周期（memory 进程内存 / postgres PG 持久化）。
    """

    async def setup(self) -> None:
        """初始化存储（postgres 建表；memory 无操作）。"""

    async def upsert(
        self,
        user_id: int | None,
        session_id: str,
        scope: str,
        title: str,
        last_message: str | None,
        message_count: int,
    ) -> None:
        """写入/更新一轮会话元数据（title 首次定稿，后续不覆盖）。"""

    async def list_by_user(self, user_id: int) -> list[SessionRecord]:
        """列出某用户的历史会话（C 端，updated_at 倒序，LIMIT 上限）。"""

    async def delete(self, user_id: int, session_id: str) -> bool:
        """删除某会话元数据（按 user_id 隔离，跨用户不可删他人会话）。

        仅删 ``agent_sessions`` 元数据；checkpoint 历史消息由调用方经
        checkpointer ``adelete_thread`` 清理（二者后端同源）。

        Returns:
            bool: 是否删到记录（False 表示会话不存在或不属于该用户）。
        """


class MemorySessionStore:
    """内存会话元数据存储（与 MemorySaver 同生命周期，开发/测试默认）。

    进程重启即失，多 worker 不共享——与 memory checkpointer 行为一致。
    """

    def __init__(self) -> None:
        self._records: dict[tuple[int | None, str], dict[str, Any]] = {}

    async def setup(self) -> None:
        return

    async def upsert(
        self,
        user_id: int | None,
        session_id: str,
        scope: str,
        title: str,
        last_message: str | None,
        message_count: int,
    ) -> None:
        now = datetime.now(UTC).isoformat()
        key = (user_id, session_id)
        existing = self._records.get(key)
        if existing is None:
            # 首次落库：写入首条消息标题，之后不覆盖（首条消息定标题）
            self._records[key] = {
                "user_id": user_id,
                "session_id": session_id,
                "scope": scope,
                "title": title,
                "last_message": last_message,
                "message_count": message_count,
                "updated_at": now,
            }
        else:
            existing["last_message"] = last_message
            existing["message_count"] = existing.get("message_count", 0) + message_count
            existing["updated_at"] = now

    async def list_by_user(self, user_id: int) -> list[SessionRecord]:
        records = [r for (uid, _sid), r in self._records.items() if uid == user_id]
        records.sort(key=lambda r: r["updated_at"], reverse=True)
        return [
            {
                "session_id": r["session_id"],
                "title": r["title"],
                "last_message": r["last_message"],
                "message_count": r["message_count"],
                "updated_at": r["updated_at"],
            }
            for r in records[:SESSION_LIST_LIMIT]
        ]

    async def delete(self, user_id: int, session_id: str) -> bool:
        """删除会话元数据（MemorySaver 同生命周期，开发/测试）。

        Args:
            user_id: 用户 ID（按 user_id 隔离）。
            session_id: 会话 ID。

        Returns:
            bool: 是否删到记录。
        """
        key = (user_id, session_id)
        return self._records.pop(key, None) is not None


class PostgresSessionStore:
    """PG 会话元数据存储（生产，长期持久化）。

    复用 ``checkpointer.get_pg_pool()`` 全局连接池（不新建池）。依赖 psycopg_pool
    （postgres 后端已由 checkpointer 保障其安装）。
    """

    _DDL = """
        CREATE TABLE IF NOT EXISTS agent_sessions (
            user_id       BIGINT       NOT NULL,
            session_id    TEXT         NOT NULL,
            scope         TEXT         NOT NULL DEFAULT 'c_end',
            title         TEXT         NOT NULL DEFAULT '新会话',
            last_message  TEXT,
            message_count INTEGER      NOT NULL DEFAULT 1,
            created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
            updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
            PRIMARY KEY (user_id, session_id)
        )
    """

    def __init__(self) -> None:
        from app.orchestrator.checkpointer import get_pg_pool

        self._pool: AsyncConnectionPool[AsyncConnection[dict[str, Any]]] | None = get_pg_pool()

    async def setup(self) -> None:
        if self._pool is None:
            return
        async with self._pool.connection() as conn:
            await conn.execute(self._DDL)
        logger.info("Postgres 会话元数据表 agent_sessions 已就绪")

    async def upsert(
        self,
        user_id: int | None,
        session_id: str,
        scope: str,
        title: str,
        last_message: str | None,
        message_count: int,
    ) -> None:
        if self._pool is None:
            return
        async with self._pool.connection() as conn:
            # title 不在 DO UPDATE 中更新——首条消息定标题（首次 INSERT 写入）
            await conn.execute(
                """
                INSERT INTO agent_sessions
                    (user_id, session_id, scope, title, last_message, message_count)
                VALUES (%s, %s, %s, %s, %s, %s)
                ON CONFLICT (user_id, session_id)
                DO UPDATE SET
                    last_message = EXCLUDED.last_message,
                    message_count = agent_sessions.message_count + EXCLUDED.message_count,
                    updated_at = now()
                """,
                (user_id, session_id, scope, title, last_message, message_count),
            )

    async def list_by_user(self, user_id: int) -> list[SessionRecord]:
        if self._pool is None:
            return []
        async with self._pool.connection() as conn:
            cur = await conn.execute(
                """
                SELECT session_id, title, last_message, message_count, updated_at
                FROM agent_sessions
                WHERE user_id = %s AND scope = 'c_end'
                ORDER BY updated_at DESC
                LIMIT %s
                """,
                (user_id, SESSION_LIST_LIMIT),
            )
            rows = await cur.fetchall()
        return [
            {
                "session_id": r["session_id"],
                "title": r["title"],
                "last_message": r["last_message"],
                "message_count": r["message_count"],
                "updated_at": (r["updated_at"].isoformat() if r["updated_at"] is not None else ""),
            }
            for r in rows
        ]

    async def delete(self, user_id: int, session_id: str) -> bool:
        """删除会话元数据（PG 持久化，生产）。

        Args:
            user_id: 用户 ID（WHERE 过滤，跨用户不可删他人会话）。
            session_id: 会话 ID。

        Returns:
            bool: 是否删到记录（rowcount > 0）。
        """
        if self._pool is None:
            return False
        async with self._pool.connection() as conn:
            cur = await conn.execute(
                "DELETE FROM agent_sessions WHERE user_id = %s AND session_id = %s",
                (user_id, session_id),
            )
            return cur.rowcount > 0


# 进程级单例（与 checkpointer 后端绑定后不再切换）
_session_store: SessionStore | None = None


def get_session_store() -> SessionStore:
    """返回会话元数据存储单例（按 checkpointer_backend 选择实现）。

    Returns:
        postgres 后端 -> PostgresSessionStore；其余（memory）-> MemorySessionStore。
    """
    global _session_store
    if _session_store is None:
        backend = get_settings().checkpointer_backend
        if backend == "postgres":
            _session_store = PostgresSessionStore()
        else:
            _session_store = MemorySessionStore()
        logger.info("会话元数据存储后端: %s", backend)
    return _session_store


def _reset_session_store() -> None:
    """重置单例（仅供测试隔离）。"""
    global _session_store
    _session_store = None
