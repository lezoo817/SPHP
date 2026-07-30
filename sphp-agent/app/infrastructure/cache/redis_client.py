"""Redis 客户端（系分 §4.4 基础设施层）。

统一管理 Redis 连接池，提供 confirm_token 存取等操作。
"""

import json
import logging
from functools import lru_cache

import redis.asyncio as redis

from app.infrastructure.config.settings import get_settings

logger = logging.getLogger(__name__)


@lru_cache
def get_redis() -> redis.Redis:
    """获取 Redis 连接（单例）。

    连接池配置（系分 §10.4）：max_connections=50
    """
    s = get_settings()
    return redis.Redis(
        host=s.redis_host,
        port=s.redis_port,
        password=s.redis_password,
        decode_responses=True,
        max_connections=50,
    )


async def close_redis() -> None:
    """关闭 Redis 连接（优雅关闭时调用）。"""
    r = get_redis()
    await r.aclose()
    logger.info("Redis connection closed")


# ---- confirm_token 操作（系分 §5.5）----

CONFIRM_TOKEN_PREFIX = "confirm"

async def set_confirm_token(
    token_id: str,
    session_id: str,
    user_id: str,
    tool_name: str,
    tool_arguments: dict,
    card_type: str,
    ttl: int | None = None,
) -> None:
    """存储确认 token（系分 §5.5，5min TTL，一次性消费）。

    Redis key 模式: confirm:{session_id}:{tool_name}:{token_id}
    """
    s = get_settings()
    ttl = ttl or s.confirm_token_ttl

    key = f"{CONFIRM_TOKEN_PREFIX}:{session_id}:{tool_name}:{token_id}"
    value = json.dumps({
        "token_id": token_id,
        "session_id": session_id,
        "user_id": user_id,
        "tool_name": tool_name,
        "tool_arguments": tool_arguments,
        "card_type": card_type,
    }, ensure_ascii=False)

    client = get_redis()
    await client.setex(key, ttl, value)


async def get_and_delete_confirm_token(
    key: str,
    expected_user_id: str,
) -> dict | None:
    """原子性获取并删除确认 token（系分 §5.5 Lua 脚本）。

    校验 user_id 匹配，不匹配返回 None。
    """
    # Lua 脚本保证原子性
    lua_script = """
    local key = KEYS[1]
    local expected_user_id = ARGV[1]
    local value = redis.call('GET', key)
    if value == false then
        return nil
    end
    local data = cjson.decode(value)
    if data.user_id ~= expected_user_id then
        return nil
    end
    redis.call('DEL', key)
    return value
    """

    client = get_redis()
    result = await client.eval(lua_script, 1, key, expected_user_id)
    if result is None:
        return None
    return json.loads(result)


# ---- 限流操作（系分 §10.4）----

async def check_rate_limit(user_id: str, limit: int, window: int = 60) -> bool:
    """滑动窗口限流检查。"""
    import time
    client = get_redis()
    key = f"rate_limit:{user_id}"
    now = time.time()

    pipe = client.pipeline()
    pipe.zremrangebyscore(key, 0, now - window)
    pipe.zcard(key)
    pipe.zadd(key, {str(now): now})
    pipe.expire(key, window)
    results = await pipe.execute()

    count = results[1]
    return count < limit
