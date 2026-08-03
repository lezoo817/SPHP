"""按用户限流中间件（系分 §10.4 限流保护）。

使用 Redis 滑动窗口限流，默认每用户每分钟 20 次请求。
Redis 不可用 / 连接池耗尽时降级为进程内滑动窗口计数（P1-4），
避免"Redis 故障即全放行"（攻击者耗尽连接池即可绕过限流）。

限流键优先取 JWT 中间件注入的 user_id，回退客户端 IP。
"""

import logging

from fastapi import Request
from starlette.middleware.base import BaseHTTPMiddleware, RequestResponseEndpoint
from starlette.responses import JSONResponse, Response

from app.api.middleware.memory_rate_limit import check_in_memory_rate_limit
from app.infrastructure.cache.redis_client import check_rate_limit
from app.infrastructure.config.settings import get_settings

logger = logging.getLogger(__name__)


async def is_rate_limited(key: str) -> bool:
    """检查限流键是否超限（True=超限应拒绝）。

    Redis 正常走滑动窗口；Redis 不可用 / 连接池耗尽时降级为进程内滑动窗口
    计数（P1-4），避免 fail-open 全放行。供限流中间件与 JWT 中间件共用
    （JWT 的无效 token 分支按 IP 计数，P1-5 防洪泛绕过）。

    Args:
        key: 限流键（user_id 或 IP，或带业务前缀的复合键）。

    Returns:
        True = 超限（应返回 429）；False = 放行。
    """
    settings = get_settings()
    try:
        allowed = await check_rate_limit(key, settings.rate_limit_per_minute)
    except Exception as e:
        # P1-4：Redis 不可用 / 连接池耗尽时，不再 fail-open 全放行——
        # 降级为进程内滑动窗口计数（与 Redis 语义一致的兜底），
        # 防止攻击者耗尽连接池后绕过限流（实测 300 并发全放行）。
        # 单进程生效，Redis 恢复后自动切回精确限流。
        logger.warning("限流 Redis 不可用，降级进程内计数: %s", e)
        allowed = check_in_memory_rate_limit(key, settings.rate_limit_per_minute)
    return not allowed


def rate_limited_response(request: Request) -> JSONResponse:
    """构造 429 统一信封响应（系分 §6.1，RATE_LIMITED 错误码）。"""
    return JSONResponse(
        status_code=429,
        content={
            "code": "RATE_LIMITED",
            "message": "请求频率超过限制，请稍后再试",
            "data": None,
            "traceId": getattr(request.state, "trace_id", ""),
        },
    )


class RateLimitMiddleware(BaseHTTPMiddleware):
    """Redis 滑动窗口限流（复用 redis_client.check_rate_limit，避免重复实现）。"""

    EXEMPT_PATHS = {"/health", "/docs", "/openapi.json", "/redoc"}

    async def dispatch(self, request: Request, call_next: RequestResponseEndpoint) -> Response:
        """请求入口：按 user_id（回退 IP）限流，超限返回 429 统一信封。

        豁免路径（/health /docs 等）直接放行；限流键优先取 JWT 中间件注入
        的 user_id，匿名请求回退客户端 IP；Redis 不可用时由
        ``is_rate_limited`` 降级进程内计数（P1-4）。
        """
        if request.url.path in self.EXEMPT_PATHS:
            return await call_next(request)

        # 限流键：优先 user_id（JWT 中间件注入），回退客户端 IP
        user_id = getattr(request.state, "user_id", None)
        if user_id is not None:
            key = str(user_id)
        else:
            key = request.client.host if request.client else "anonymous"

        if await is_rate_limited(key):
            return rate_limited_response(request)

        return await call_next(request)
