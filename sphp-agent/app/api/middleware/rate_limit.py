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


class RateLimitMiddleware(BaseHTTPMiddleware):
    """Redis 滑动窗口限流（复用 redis_client.check_rate_limit，避免重复实现）。"""

    EXEMPT_PATHS = {"/health", "/docs", "/openapi.json", "/redoc"}

    async def dispatch(self, request: Request, call_next: RequestResponseEndpoint) -> Response:
        if request.url.path in self.EXEMPT_PATHS:
            return await call_next(request)

        # 限流键：优先 user_id（JWT 中间件注入），回退客户端 IP
        user_id = getattr(request.state, "user_id", None)
        if user_id is None:
            user_id = request.client.host if request.client else "anonymous"

        settings = get_settings()
        try:
            allowed = await check_rate_limit(str(user_id), settings.rate_limit_per_minute)
        except Exception as e:
            # P1-4：Redis 不可用 / 连接池耗尽时，不再 fail-open 全放行——
            # 降级为进程内滑动窗口计数（与 Redis 语义一致的兜底），
            # 防止攻击者耗尽连接池后绕过限流（实测 300 并发全放行）。
            # 单进程生效，Redis 恢复后自动切回精确限流。
            logger.warning("限流 Redis 不可用，降级进程内计数: %s", e)
            allowed = check_in_memory_rate_limit(str(user_id), settings.rate_limit_per_minute)

        if not allowed:
            # 系分 §6.1 统一信封 + RATE_LIMITED 错误码（429）
            return JSONResponse(
                status_code=429,
                content={
                    "code": "RATE_LIMITED",
                    "message": "请求频率超过限制，请稍后再试",
                    "data": None,
                    "traceId": getattr(request.state, "trace_id", ""),
                },
            )

        return await call_next(request)
