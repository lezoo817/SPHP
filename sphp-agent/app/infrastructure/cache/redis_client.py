"""Redis 客户端。

统一管理 Redis 连接池，提供 confirm_token 存取等操作。
当前为占位，待 Redis 可用后替换 executor 中的内存 _pending dict。
"""

from functools import lru_cache

import redis.asyncio as redis


@lru_cache
def get_redis() -> redis.Redis:
    """获取 Redis 连接（单例）。"""
    # TODO: 从 settings 读取 REDIS_URL
    return redis.Redis(
        host="localhost",
        port=6379,
        decode_responses=True,
    )


async def set_confirm_token(token: str, data: dict, ttl: int = 300) -> None:
    """存储确认 token（5 分钟自动过期）。

    Args:
        token: confirm_token。
        data: 待确认的工具调用数据。
        ttl: 过期秒数（默认 300 = 5 分钟）。
    """
    import json
    client = get_redis()
    await client.setex(f"confirm:{token}", ttl, json.dumps(data))


async def get_confirm_token(token: str) -> dict | None:
    """读取并删除确认 token（一次性消费）。

    Args:
        token: confirm_token。

    Returns:
        存储的工具调用数据，不存在或已过期返回 None。
    """
    import json
    client = get_redis()
    key = f"confirm:{token}"
    raw = await client.get(key)
    if raw is None:
        return None
    await client.delete(key)  # 一次性消费，防止重放
    return json.loads(raw)
