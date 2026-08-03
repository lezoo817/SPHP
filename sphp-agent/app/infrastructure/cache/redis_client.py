"""Redis 客户端（系分 §4.4 基础设施层）。

统一管理 Redis 连接池，提供 confirm_token 存取等操作。
"""

import json
import logging
import re
import uuid
from functools import lru_cache
from typing import Any, cast

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
    """关闭 Redis 连接（优雅关闭时调用）。

    清除 lru_cache，避免后续 get_redis() 返回已关闭的死连接
    （热重载/测试/优雅关闭场景下会触发）。
    """
    r = get_redis()
    await r.aclose()
    get_redis.cache_clear()
    logger.info("Redis connection closed")


# ---- confirm_token 操作（系分 §5.5）----

CONFIRM_TOKEN_PREFIX = "confirm"


async def set_confirm_token(
    token_id: str,
    session_id: str,
    user_id: str,
    tool_name: str,
    tool_arguments: dict[str, Any],
    card_type: str,
    ttl: int | None = None,
) -> None:
    """存储确认 token（系分 §5.5，5min TTL，一次性消费）。

    Redis key 模式: confirm:{session_id}:{tool_name}:{token_id}
    """
    s = get_settings()
    ttl = ttl or s.confirm_token_ttl

    key = f"{CONFIRM_TOKEN_PREFIX}:{session_id}:{tool_name}:{token_id}"
    value = json.dumps(
        {
            "token_id": token_id,
            "session_id": session_id,
            "user_id": user_id,
            "tool_name": tool_name,
            "tool_arguments": tool_arguments,
            "card_type": card_type,
        },
        ensure_ascii=False,
    )

    client = get_redis()
    await client.set(key, value, ex=ttl)


async def get_and_delete_confirm_token_by_token(
    session_id: str,
    token_id: str,
    expected_user_id: str,
) -> dict[str, Any] | None:
    """按前端回传的 confirm_token 原子消费（系分 §5.5，Lua 一次性）。

    /chat/confirm 只回传 session_id + confirm_token，而 Redis key 是
    ``confirm:{session_id}:{tool_name}:{token_id}``（tool_name 未知），
    因此按 session_id 前缀 SCAN 匹配 token_id 后缀，再校验 user_id 后
    一次性 GET + DEL。Lua 脚本整体原子执行，多游标 SCAN 亦安全。

    Args:
        session_id: 前端回传的会话 ID，用于构造前缀 confirm:{session_id}:*
        token_id: 前端回传的确认令牌（UUID4），匹配 key 后缀
        expected_user_id: 当前请求用户 ID，与值内 user_id 比对

    Returns:
        匹配且校验通过时返回 token 记录 dict；未匹配 / 用户不匹配返回 None。
    """
    # Lua 脚本：SCAN 前缀匹配 token_id，校验 user_id，GET + DEL
    lua_script = """
    local session_id = ARGV[1]
    local token_id = ARGV[2]
    local expected_user_id = ARGV[3]
    local pattern = 'confirm:' .. session_id .. ':*'
    local cursor = '0'
    local suffix = ':' .. token_id
    local value = false

    repeat
        local scan_result = redis.call('SCAN', cursor, 'MATCH', pattern, 'COUNT', 50)
        cursor = scan_result[1]
        local keys = scan_result[2]
        for i, key in ipairs(keys) do
            -- 匹配 token_id 后缀（key 以 :{token_id} 结尾）
            local len = #key
            local suffix_start = len - #suffix + 1
            if suffix_start >= 1 and string.sub(key, suffix_start) == suffix then
                local v = redis.call('GET', key)
                if v ~= false then
                    local data = cjson.decode(v)
                    if data.user_id == expected_user_id then
                        redis.call('DEL', key)
                        value = v
                        break
                    end
                end
            end
        end
        if value ~= false then
            break
        end
    until cursor == '0'

    if value == false then
        return nil
    end
    return value
    """

    client = get_redis()
    # Lua 脚本只用 ARGV（session/token/user_id 均为参数），numkeys=0
    # session_id 先做 glob 转义：防止前端注入 glob 元字符越权 SCAN 其他会话
    result = await client.eval(lua_script, 0, _escape_glob(session_id), token_id, expected_user_id)
    if result is None:
        return None
    return cast(dict[str, Any], json.loads(result))


# ---- 已确认操作回执（M5-T4 / T-M3-L1）----

# Redis SCAN MATCH / KEYS 的 glob 元字符（转义符为反斜杠，与 Lua 模式的 % 不同）
_GLOB_META = re.compile(r"([*?\[\]\\])")


def _escape_glob(text: str) -> str:
    """转义 Redis glob 元字符，防止 session_id 注入 SCAN 匹配（安全修复）。

    Redis 的 ``SCAN MATCH`` / ``KEYS`` 使用 glob 语义，元字符为 ``*`` ``?``
    ``[]``，转义符为反斜杠 ``\\``（注意不是 Lua 模式的 ``%``）。session_id
    直接来自前端不可信，若不转义，传入 ``*`` 即可匹配并读取+删除全部会话的
    确认回执（内容含工具名与操作业务数据，属患者相关数据）。合法 session_id
    中的 ``-``/``_``/``.`` 非 glob 元字符，不会被转义，不影响正常匹配。

    Args:
        text: 原始 session_id（或任意拼接进 glob pattern 的用户输入）。

    Returns:
        str: 转义后的字符串，可安全拼入 ``SCAN MATCH`` 的 pattern。
    """
    return _GLOB_META.sub(r"\\\1", text)


CONFIRM_DONE_PREFIX = "confirm_done"


async def set_confirm_done(
    session_id: str,
    tool_name: str,
    action_result: object,
    ttl: int | None = None,
) -> None:
    """记录已确认并执行成功的操作（T-M3-L1，供下一轮对话引用）。

    /chat/confirm 是独立 HTTP 请求，执行成功结果不进入 graph 状态。
    写入该回执后，下一轮 ``chat_stream`` 一次性消费注入会话上下文，
    使"确认后追问"的对话保持连续。

    Args:
        session_id: 会话 ID。
        tool_name: 已执行的 L2 工具名。
        action_result: 工具执行返回的业务数据（Java 信封内层）。
        ttl: 回执 TTL（秒），默认 settings.confirm_done_ttl。
    """
    s = get_settings()
    ttl = ttl or s.confirm_done_ttl

    key = f"{CONFIRM_DONE_PREFIX}:{session_id}:{tool_name}:{uuid.uuid4()}"
    value = json.dumps(
        {"tool_name": tool_name, "action_result": action_result},
        ensure_ascii=False,
    )

    client = get_redis()
    await client.set(key, value, ex=ttl)


async def get_and_delete_confirm_done(session_id: str) -> list[dict[str, Any]]:
    """读取并删除某会话全部已确认操作回执（一次性消费，Lua 原子）。

    注入会话上下文后即删除——注入的 system 消息随 LangGraph checkpointer
    持久化到消息历史，后续轮次持续可见，无需重复注入。

    Args:
        session_id: 会话 ID，匹配前缀 ``confirm_done:{session_id}:*``。

    Returns:
        list[dict]: 全部回执记录列表（含 tool_name / action_result）；
            无记录时返回空列表。
    """
    lua_script = """
    local session_id = ARGV[1]
    local pattern = 'confirm_done:' .. session_id .. ':*'
    local cursor = '0'
    local results = {}

    repeat
        local scan_result = redis.call('SCAN', cursor, 'MATCH', pattern, 'COUNT', 50)
        cursor = scan_result[1]
        local keys = scan_result[2]
        for i, key in ipairs(keys) do
            local v = redis.call('GET', key)
            if v ~= false then
                redis.call('DEL', key)
                table.insert(results, v)
            end
        end
    until cursor == '0'

    if #results == 0 then
        return nil
    end
    return results
    """

    client = get_redis()
    # session_id 先做 glob 转义：防止注入 '*' 等元字符匹配并删除全部会话回执
    result = await client.eval(lua_script, 0, _escape_glob(session_id))
    if result is None:
        return []
    return [json.loads(v) for v in result]


# ---- 限流操作（系分 §10.4）----


async def check_rate_limit(user_id: str, limit: int, window: int = 60) -> bool:
    """滑动窗口限流检查（Lua 脚本原子执行，拒绝请求不计数）。

    Bug 5 修复：使用 Lua 脚本单次往返完成"清旧 + 计数 + 条件写入"，
    被拒绝的请求不会写入 ZADD（不会延长锁定时间）。

    P1-4 修复：ZADD member 由 ``now`` 改为 ``now:<随机后缀>``。原实现
    member=score=now，同一 tick（time.time() 分辨率内）到达的多个请求 member
    相同，ZADD 按 member 去重合并为一条，窗口计数被低估、突发流量漏放。

    Args:
        user_id: 用户标识，构造 key rate_limit:{user_id}。
        limit: 窗口内允许的最大请求数。
        window: 窗口大小（秒），默认 60。

    Returns:
        True = 放行（在窗口内），False = 拒绝（超限）。
    """
    lua_script = """
    local key = KEYS[1]
    local limit = tonumber(ARGV[1])
    local window = tonumber(ARGV[2])
    local now = tonumber(ARGV[3])

    redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
    local count = redis.call('ZCARD', key)
    if count < limit then
        redis.call('ZADD', key, now, ARGV[4])
        redis.call('EXPIRE', key, window)
        return 1
    end
    return 0
    """

    client = get_redis()
    key = f"rate_limit:{user_id}"
    import time

    now = time.time()
    # 随机后缀保证同一 tick 内 member 唯一（score 仍为 now，可滑动窗口剪枝）
    member = f"{now}:{uuid.uuid4().hex}"
    result = await client.eval(lua_script, 1, key, str(limit), str(window), str(now), member)
    return bool(result == 1)
