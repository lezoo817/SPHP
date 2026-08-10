"""交互卡片历史存储层（2026-08-10 卡片持久化）。

AI 对话中的四类交互卡片（``card`` L2 确认卡 / ``action_card`` 受控交互卡 /
``record_picker`` 记录选择卡 / ``options`` 选医生选择卡）原只经 SSE 实时下发、
不入库，导致历史会话回看与支付完成后返回对话时卡片丢失。本层按
``settings.checkpointer_backend`` 选择实现，**生命周期与 checkpointer 一致**：

- ``memory``：MemoryCardStore（进程内存 dict，与 MemorySaver 同生命周期，
  进程重启即失——开发已知限制）
- ``postgres``：PostgresCardStore（PG ``agent_cards`` 表，长期持久化，
  生产历史卡片可回溯）

卡片持久化与 LangGraph checkpoint（纯文本消息）**互补**：checkpoint 负责
"历史消息气泡"，本表负责"历史交互卡片"（payload 与 SSE 实时推送完全一致，
前端可复用同一渲染组件）。写入点在 SSE 层（``chat._sse_generator`` 正常完成
路径），按 ``(user_id, session_id)`` 隔离，跨用户不可读他人会话卡片。
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


class CardRecord(TypedDict):
    """单条历史卡片记录（对外返回字段，payload 与 SSE 推送一致）。"""

    seq: int
    event: str  # card / action_card / record_picker / options
    payload: dict[str, Any]
    created_at: str  # ISO 8601 时间
    # 卡片锚点（2026-08-10）：该卡片产生轮次结束时可见消息（user/assistant）
    # 总数，历史接口据此把卡片插回对话中对应位置（而非堆到末尾）。
    anchor: int
    # L2 确认卡是否已被用户确认成功（2026-08-10）：confirm 端点成功回调后置 True，
    # 历史重放据此把确认过的卡片渲染为"已完成"而非"待确认"。
    confirmed: bool


class CardStore(Protocol):
    """交互卡片历史存储接口。

    与 checkpointer 同生命周期（memory 进程内存 / postgres PG 持久化）。
    """

    async def setup(self) -> None:
        """初始化存储（postgres 建表；memory 无操作）。"""

    async def append_batch(
        self,
        user_id: int | None,
        session_id: str,
        cards: list[tuple[str, dict[str, Any]]],
        anchor: int,
    ) -> None:
        """按序追加一批卡片事件（``(event, payload)``），``seq`` 自增。

        由 SSE 层会话锁保证同会话串行，``seq`` 无并发竞争；跨会话天然并行。
        ``anchor`` 为本批卡片共同的消息锚点（该轮结束时可见消息总数），供
        历史重放时把卡片插回对应位置。

        Args:
            user_id: 用户 ID（匿名为 None，PG 下 NOT NULL 违反被调用方吞掉）。
            session_id: 会话 ID（外部契约）。
            cards: 本轮产生的卡片事件列表，顺序即前端展示顺序。
            anchor: 本轮结束时可见消息总数（卡片应插到第 anchor 条消息之后）。
        """

    async def list_by_session(
        self, user_id: int, session_id: str
    ) -> list[CardRecord]:
        """读取某会话全部卡片（``seq`` 升序，供历史接口重放）。

        Args:
            user_id: 用户 ID（按 user_id 隔离，跨用户读不到他人会话）。
            session_id: 会话 ID。

        Returns:
            list[CardRecord]: 历史卡片记录列表。
        """

    async def mark_confirmed(
        self, user_id: int | None, session_id: str, confirm_token: str
    ) -> bool:
        """把指定 L2 确认卡标记为已确认（confirm 端点成功后调用）。

        Args:
            user_id: 用户 ID（按 user_id 隔离；匿名为 None，PG 下匹配不到无副作用）。
            session_id: 会话 ID。
            confirm_token: 卡片 payload 的 confirm_token（与确认请求一致）。

        Returns:
            bool: 是否命中并更新了卡片（未命中/非 L2 卡返回 False，不阻塞）。
        """

    async def delete(self, user_id: int, session_id: str) -> bool:
        """删除某会话全部卡片（按 user_id 隔离，随会话删除联动清理）。

        Args:
            user_id: 用户 ID（WHERE 过滤，跨用户不可删他人会话）。
            session_id: 会话 ID。

        Returns:
            bool: 是否删到记录。
        """


class MemoryCardStore:
    """内存卡片历史存储（与 MemorySaver 同生命周期，开发/测试默认）。

    进程重启即失，多 worker 不共享——与 memory checkpointer 行为一致。
    """

    def __init__(self) -> None:
        self._records: dict[tuple[int | None, str], list[dict[str, Any]]] = {}

    async def setup(self) -> None:
        return

    async def append_batch(
        self,
        user_id: int | None,
        session_id: str,
        cards: list[tuple[str, dict[str, Any]]],
        anchor: int,
    ) -> None:
        if not cards:
            return
        records = self._records.setdefault((user_id, session_id), [])
        now = datetime.now(UTC).isoformat()
        base = len(records)
        for i, (event, payload) in enumerate(cards):
            records.append(
                {
                    "seq": base + i + 1,
                    "event": event,
                    "payload": payload,
                    "created_at": now,
                    "anchor": anchor,
                    "confirmed": False,
                }
            )

    async def list_by_session(
        self, user_id: int, session_id: str
    ) -> list[CardRecord]:
        records = self._records.get((user_id, session_id), [])
        return [
            {
                "seq": r["seq"],
                "event": r["event"],
                "payload": r["payload"],
                "created_at": r["created_at"],
                "anchor": r["anchor"],
                "confirmed": r.get("confirmed", False),
            }
            for r in records
        ]

    async def mark_confirmed(
        self, user_id: int | None, session_id: str, confirm_token: str
    ) -> bool:
        """把指定 L2 确认卡标记为已确认（内存实现，开发/测试）。

        Args:
            user_id: 用户 ID（按 user_id 隔离）。
            session_id: 会话 ID。
            confirm_token: 卡片 payload 的 confirm_token。

        Returns:
            bool: 是否命中并更新了卡片。
        """
        for r in self._records.get((user_id, session_id), []):
            payload = r.get("payload") or {}
            if payload.get("confirm_token") == confirm_token:
                r["confirmed"] = True
                return True
        return False

    async def delete(self, user_id: int, session_id: str) -> bool:
        """删除会话全部卡片（内存实现，开发/测试）。

        Args:
            user_id: 用户 ID（按 user_id 隔离）。
            session_id: 会话 ID。

        Returns:
            bool: 是否删到记录。
        """
        key = (user_id, session_id)
        return self._records.pop(key, None) is not None


class PostgresCardStore:
    """PG 卡片历史存储（生产，长期持久化）。

    复用 ``checkpointer.get_pg_pool()`` 全局连接池（不新建池）。依赖 psycopg_pool
    与 psycopg 3.x（postgres 后端已由 checkpointer 保障其安装）。
    """

    _DDL = """
        CREATE TABLE IF NOT EXISTS agent_cards (
            user_id     BIGINT       NOT NULL,
            session_id  TEXT         NOT NULL,
            seq         BIGINT       NOT NULL,
            event       TEXT         NOT NULL,
            payload     JSONB        NOT NULL,
            anchor      INTEGER      NOT NULL DEFAULT 0,
            confirmed   BOOLEAN      NOT NULL DEFAULT false,
            created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
            PRIMARY KEY (user_id, session_id, seq)
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
            # 2026-08-10 新列幂等迁移：对已存在的表（旧 DDL 无这些列）追加
            await conn.execute(
                "ALTER TABLE agent_cards "
                "ADD COLUMN IF NOT EXISTS anchor INTEGER NOT NULL DEFAULT 0"
            )
            await conn.execute(
                "ALTER TABLE agent_cards "
                "ADD COLUMN IF NOT EXISTS confirmed BOOLEAN NOT NULL DEFAULT false"
            )
        logger.info("PG 卡片历史表 agent_cards 已就绪")

    async def append_batch(
        self,
        user_id: int | None,
        session_id: str,
        cards: list[tuple[str, dict[str, Any]]],
        anchor: int,
    ) -> None:
        if self._pool is None or not cards:
            return
        from psycopg.types.json import Jsonb

        async with self._pool.connection() as conn:
            # MAX(seq)+1：同会话由 SSE 层会话锁保证串行，无并发竞争。
            cur = await conn.execute(
                "SELECT COALESCE(MAX(seq), 0) AS m FROM agent_cards "
                "WHERE user_id = %s AND session_id = %s",
                (user_id, session_id),
            )
            row = await cur.fetchone()
            base = row["m"] if row else 0
            for i, (event, payload) in enumerate(cards):
                await conn.execute(
                    "INSERT INTO agent_cards (user_id, session_id, seq, event, payload, anchor) "
                    "VALUES (%s, %s, %s, %s, %s, %s)",
                    (user_id, session_id, base + i + 1, event, Jsonb(payload), anchor),
                )

    async def list_by_session(
        self, user_id: int, session_id: str
    ) -> list[CardRecord]:
        if self._pool is None:
            return []
        async with self._pool.connection() as conn:
            cur = await conn.execute(
                "SELECT seq, event, payload, anchor, confirmed, created_at FROM agent_cards "
                "WHERE user_id = %s AND session_id = %s ORDER BY seq ASC",
                (user_id, session_id),
            )
            rows = await cur.fetchall()
        return [
            {
                "seq": r["seq"],
                "event": r["event"],
                "payload": r["payload"],
                "anchor": r["anchor"],
                "confirmed": r["confirmed"],
                "created_at": (r["created_at"].isoformat() if r["created_at"] is not None else ""),
            }
            for r in rows
        ]

    async def mark_confirmed(
        self, user_id: int | None, session_id: str, confirm_token: str
    ) -> bool:
        """把指定 L2 确认卡标记为已确认（PG 持久化，生产）。

        Args:
            user_id: 用户 ID（WHERE 过滤，跨用户不可改他人会话；None 匹配不到）。
            session_id: 会话 ID。
            confirm_token: 卡片 payload 的 confirm_token。

        Returns:
            bool: 是否命中并更新了卡片（rowcount > 0）。
        """
        if self._pool is None:
            return False
        async with self._pool.connection() as conn:
            cur = await conn.execute(
                "UPDATE agent_cards SET confirmed = true "
                "WHERE user_id = %s AND session_id = %s "
                "AND payload->>'confirm_token' = %s",
                (user_id, session_id, confirm_token),
            )
            return cur.rowcount > 0

    async def delete(self, user_id: int, session_id: str) -> bool:
        """删除会话全部卡片（PG 持久化，生产）。

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
                "DELETE FROM agent_cards WHERE user_id = %s AND session_id = %s",
                (user_id, session_id),
            )
            return cur.rowcount > 0


# 进程级单例（与 checkpointer 后端绑定后不再切换）
_card_store: CardStore | None = None


def get_card_store() -> CardStore:
    """返回卡片历史存储单例（按 checkpointer_backend 选择实现）。

    Returns:
        postgres 后端 -> PostgresCardStore；其余（memory）-> MemoryCardStore。
    """
    global _card_store
    if _card_store is None:
        backend = get_settings().checkpointer_backend
        if backend == "postgres":
            _card_store = PostgresCardStore()
        else:
            _card_store = MemoryCardStore()
        logger.info("卡片历史存储后端: %s", backend)
    return _card_store


def _reset_card_store() -> None:
    """重置单例（仅供测试隔离）。"""
    global _card_store
    _card_store = None
