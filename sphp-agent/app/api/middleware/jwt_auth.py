"""JWT 鉴权中间件（系分 §6.3 方案 A）。

Agent 收到前端 JWT 后，调 Java token 解析接口校验并换取 userId。
C 端: GET /api/c/v1/auth/token/parse
B 端: GET /api/b/auth/token/parse（待 Java 补充）

前端通过 ``Authorization: Bearer <token>`` 传 JWT，通过 ``X-Scope: c_end|b_end``
传服务端（默认 c_end）。校验通过后将 userId / scope 注入 request.state，
供限流中间件与编排层使用。

失败策略：debug 模式降级为匿名（便于本地测试），生产模式返回 401。
"""

import logging
from typing import Any

import httpx
from fastapi import Request
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.responses import JSONResponse

from app.infrastructure.config.settings import get_settings

logger = logging.getLogger(__name__)

# Java token/parse 调用超时（秒）
_AUTH_TIMEOUT = 10.0


class JWTAuthMiddleware(BaseHTTPMiddleware):
    """JWT 鉴权中间件：校验 Bearer token，注入 userId / scope 到 request.state。"""

    # 不需要鉴权的路径
    EXEMPT_PATHS = {"/health", "/docs", "/openapi.json", "/redoc"}

    async def dispatch(self, request: Request, call_next):
        if request.url.path in self.EXEMPT_PATHS:
            return await call_next(request)

        settings = get_settings()
        auth_header = request.headers.get("Authorization", "")
        scope = request.headers.get("X-Scope", "c_end")

        # 无 Bearer token
        if not auth_header.startswith("Bearer "):
            if settings.debug:
                logger.warning("无 Token，debug 模式降级为匿名用户")
                _inject_anonymous(request, scope)
                return await call_next(request)
            return _unauthorized("缺少有效的鉴权 Token")

        token = auth_header.removeprefix("Bearer ").strip()
        user_info = await _safe_parse_token(token, scope)

        # 校验通过
        if user_info is not None:
            request.state.user_id = user_info.get("userId")
            request.state.account = user_info.get("account")
            request.state.scope = scope
            request.state.jwt_token = token
            return await call_next(request)

        # 校验失败：debug 降级，生产拒绝
        if settings.debug:
            logger.warning("JWT 校验失败，debug 模式降级为匿名用户")
            _inject_anonymous(request, scope)
            return await call_next(request)

        return _unauthorized("Token 无效或已过期")


def _inject_anonymous(request: Request, scope: str) -> None:
    """debug 降级时注入匿名状态。"""
    request.state.user_id = None
    request.state.account = "anonymous"
    request.state.scope = scope
    request.state.jwt_token = None


def _unauthorized(message: str) -> JSONResponse:
    """返回 401 JSON 响应。"""
    return JSONResponse(
        status_code=401,
        content={"success": False, "error": {"code": "AUTH_FAILED", "message": message}},
    )


async def _safe_parse_token(token: str, scope: str) -> dict[str, Any] | None:
    """调 parse_token，捕获异常转为 None（避免中间件内抛 HTTPException）。"""
    try:
        return await parse_token(token, scope)
    except httpx.HTTPError as e:
        logger.error("鉴权服务不可达: %s", e)
        return None


async def parse_token(token: str, scope: str) -> dict[str, Any] | None:
    """调 Java token/parse 接口校验 JWT，返回用户信息。

    Args:
        token: 前端传来的 JWT（去掉 "Bearer " 前缀）
        scope: c_end / b_end

    Returns:
        {"userId": 10001, "account": "zhangsan", ...} 或 None（校验失败 / 服务不可达）。
        不抛异常，由调用方根据 None 判断。
    """
    settings = get_settings()

    if scope == "c_end":
        path = settings.c_auth_parse_path
    elif scope == "b_end":
        path = settings.b_auth_parse_path
    else:
        return None

    url = f"{settings.java_base_url}{path}"

    try:
        async with httpx.AsyncClient(timeout=_AUTH_TIMEOUT) as client:
            resp = await client.get(
                url,
                headers={"Authorization": f"Bearer {token}"},
            )
            if resp.status_code != 200:
                return None

            data = resp.json()
            # 统一字符串比较（Java 可能返回 "00000" 或 200）
            code = str(data.get("code", ""))
            if code not in ("00000", "200"):
                return None

            return data.get("data")

    except (httpx.HTTPError, ValueError) as e:
        # ValueError: resp.json() 解析失败（Java 返回非 JSON）
        logger.error("token/parse 调用失败: %s", e)
        return None
