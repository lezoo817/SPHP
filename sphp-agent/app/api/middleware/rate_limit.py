"""按用户限流中间件（系分 §10.4 限流保护）。

使用 Redis 滑动窗口限流，默认每用户每分钟 20 次请求。
Redis 不可用时降级为不限制（避免误伤正常用户）。
"""

import time
import logging

from fastapi import Request, HTTPException
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.responses import JSONResponse

from app.infrastructure.config.settings import get_settings

logger = logging.getLogger(__name__)


class RateLimitMiddleware(BaseHTTPMiddleware):
    """Redis 滑动窗口限流。"""

    EXEMPT_PATHS = {"/health", "/docs", "/openapi.json", "/redoc"}

    async def dispatch(self, request: Request, call_next):
        if request.url.path in self.EXEMPT_PATHS:
            return await call_next(request)

        # 从 request.state 获取 user_id（由 JWTAuthMiddleware 注入）
        user_id = getattr(request.state, "user_id", None) or "anonymous"

        if not await self._check_rate_limit(str(user_id)):
            return JSONResponse(
                status_code=429,
                content={
                    "success": False,
                    "error": {
                        "code": "RATE_LIMITED",
                        "message": "请求频率超过限制，请稍后再试",
                    },
                },
            )

        return await call_next(request)

    async def _check_rate_limit(self, user_id: str) -> bool:
        """Redis 滑动窗口限流检查。"""
        settings = get_settings()
        limit = settings.rate_limit_per_minute
        window = 60  # 60 秒窗口

        try:
            from app.infrastructure.cache.redis_client import get_redis
            redis = get_redis()
            key = f"rate_limit:{user_id}"
            now = time.time()

            # 滑动窗口：删除窗口外记录 → 计数 → 记录本次 → 设置过期
            pipe = redis.pipeline()
            pipe.zremrangebyscore(key, 0, now - window)
            pipe.zcard(key)
            pipe.zadd(key, {str(now): now})
            pipe.expire(key, window)
            results = await pipe.execute()

            count = results[1]
            return count < limit

        except Exception as e:
            # Redis 不可用时降级：放行，避免误伤正常用户
            logger.warning("限流 Redis 不可用，降级放行: %s", e)
            return True
