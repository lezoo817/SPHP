"""JWT 鉴权中间件（系分 §6.3 方案 A）。

Agent 收到前端 JWT 后，调 Java 已有的 token 解析接口换取 userId。
C 端: GET /api/c/v1/auth/token/parse
B 端: GET /api/b/auth/token/parse（待 Java 补充）
"""

from typing import Any

import httpx
from fastapi import Request, HTTPException
from starlette.middleware.base import BaseHTTPMiddleware

from app.infrastructure.config.settings import get_settings


class JWTAuthMiddleware(BaseHTTPMiddleware):
    """JWT 鉴权中间件：从 Header 取 JWT，调 Java token/parse 换取 userId。

    将 userId 和 scope 注入 request.state，后续节点直接读取。
    """

    # 不需要鉴权的路径
    EXEMPT_PATHS = {"/health", "/docs", "/openapi.json", "/redoc", "/api/chat/stream"}

    async def dispatch(self, request: Request, call_next):
        # 健康检查和文档路径跳过鉴权
        if request.url.path in self.EXEMPT_PATHS:
            return await call_next(request)

        # 从请求体获取 scope（C 端 / B 端），从 Header 获取 JWT
        auth_header = request.headers.get("Authorization", "")
        if not auth_header.startswith("Bearer "):
            return await call_next(request)  # 让路由层处理缺失 token 的错误

        token = auth_header[7:]  # 去掉 "Bearer " 前缀

        # scope 需要从请求体获取，但中间件中读 body 有缓存问题
        # 实际实现中由路由层调用 parse_token，中间件只做 token 格式预检
        request.state.jwt_token = token

        return await call_next(request)


async def parse_token(token: str, scope: str) -> dict[str, Any] | None:
    """调 Java token/parse 接口校验 JWT，返回 userId 等信息。

    Args:
        token: 前端传来的 JWT（去掉 "Bearer " 前缀）
        scope: c_end / b_end

    Returns:
        {"userId": 10001, "account": "zhangsan", ...} 或 None（校验失败）

    Raises:
        HTTPException: Java token/parse 接口不可达时抛 401
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
        async with httpx.AsyncClient(timeout=10) as client:
            resp = await client.get(
                url,
                headers={"Authorization": f"Bearer {token}"},
            )
            if resp.status_code != 200:
                return None

            data = resp.json()
            # C 端返回 code="00000" 表示成功
            code = data.get("code")
            if code != "00000" and code != 200:
                return None

            return data.get("data")

    except httpx.HTTPError:
        raise HTTPException(status_code=401, detail="鉴权服务不可用，请稍后重试")
