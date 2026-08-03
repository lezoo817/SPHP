"""JWT 鉴权中间件（系分 §6.3 方案 A）。

Agent 收到前端 JWT 后，调 Java token 解析接口校验并换取 userId。
C 端: GET /api/c/v1/auth/token/parse
B 端: GET /api/b/auth/token/parse（待 Java 补充）

前端通过 ``Authorization: Bearer <token>`` 传 JWT，通过 ``X-Scope: c_end|b_end``
传服务端（默认 c_end）。校验通过后将 userId / scope 注入 request.state，
供限流中间件与编排层使用。

失败策略：
    - 无 Bearer token：ALLOW_ANONYMOUS=true 时降级为匿名，否则 401
    - 携带 token 但校验失败：始终 401（不降级，保持 D1 严格策略）
"""

import logging
from typing import Any

import httpx
from fastapi import Request
from starlette.middleware.base import BaseHTTPMiddleware, RequestResponseEndpoint
from starlette.responses import JSONResponse, Response

from app.infrastructure.config.settings import get_settings

logger = logging.getLogger(__name__)

# Java token/parse 调用超时（秒）
_AUTH_TIMEOUT = 10.0


class JWTAuthMiddleware(BaseHTTPMiddleware):
    """JWT 鉴权中间件：校验 Bearer token，注入 userId / scope 到 request.state。

    无 token 且 ``ALLOW_ANONYMOUS=true`` 时降级为匿名（仅开发环境），
    匿名请求不注入 user_id，以 ``request.state.anonymous`` 标记。
    """

    # 不需要鉴权的路径
    EXEMPT_PATHS = {"/health", "/docs", "/openapi.json", "/redoc"}

    async def dispatch(self, request: Request, call_next: RequestResponseEndpoint) -> Response:
        if request.url.path in self.EXEMPT_PATHS:
            return await call_next(request)

        auth_header = request.headers.get("Authorization", "")
        scope = request.headers.get("X-Scope", "c_end")

        # 无 Bearer token
        if not auth_header.startswith("Bearer "):
            return await self._handle_no_token(request, call_next, scope)

        token = auth_header.removeprefix("Bearer ").strip()
        user_info = await _safe_parse_token(token, scope)

        # 校验通过
        if user_info is not None:
            request.state.user_id = user_info.get("userId")
            request.state.account = user_info.get("account")
            request.state.scope = scope
            request.state.jwt_token = token
            # B 端字段注入（供 API 层权限校验，如 knowledge 入库 ADMIN 校验）：
            # C 端响应无 roles 字段，默认空列表（无管理权限）
            request.state.roles = user_info.get("roles", []) or []
            request.state.dept_id = user_info.get("deptId")
            request.state.doctor_id = user_info.get("doctorId")
            request.state.hospital_id = user_info.get("hospitalId")
            return await call_next(request)

        # 携带 token 但校验失败 -> 始终 401（不降级，保持 D1 严格策略）。
        # AUTH_EXPIRED 需 Java token/parse 返回过期信号才能精确区分，
        # 当前统一归 AUTH_INVALID（同为 401，前端跳登录页）
        return _unauthorized(request, "AUTH_INVALID", "Token 无效或已过期")

    async def _handle_no_token(
        self, request: Request, call_next: RequestResponseEndpoint, scope: str
    ) -> Response:
        """无 token 分支：ALLOW_ANONYMOUS=true 时降级匿名，否则 401。

        安全（NP-1）：匿名请求无 token，无法证明 B 端医生身份，强制按 C 端处理
        （scope=c_end），忽略客户端 ``X-Scope`` 头。否则匿名用户设
        ``X-Scope: b_end`` 即可访问全部 B 端工具（query_patient_history /
        generate_draft_note 等）构成越权。B 端访问必须携带有效 B 端 JWT
        （经 token/parse 校验）。
        """
        settings = get_settings()
        if not settings.allow_anonymous:
            return _unauthorized(request, "AUTH_MISSING", "缺少有效的鉴权 Token")

        # 匿名一律 C 端，杜绝 X-Scope 伪造越权 B 端（NP-1）
        forced_scope = "c_end"
        logger.warning(
            "无 token 请求降级为匿名: path=%s, 请求 scope=%s, 强制 scope=%s",
            request.url.path,
            scope,
            forced_scope,
        )
        request.state.anonymous = True
        request.state.scope = forced_scope
        request.state.jwt_token = None
        # 不设置 user_id / account，保持 None 以区分真实用户；roles 置空无权限
        request.state.roles = []
        request.state.dept_id = None
        request.state.doctor_id = None
        request.state.hospital_id = None
        return await call_next(request)


def _unauthorized(request: Request, code: str, message: str) -> JSONResponse:
    """返回 401 统一信封 JSON 响应（系分 §6.1）。

    错误码对齐系分 §6.1 通用错误码表：AUTH_MISSING（缺少鉴权头）/
    AUTH_INVALID（token 无效）。信封格式 {code, message, data, traceId}，
    与成功路径及前端统一 request 封装（按 code / traceId 解析）一致。

    Args:
        request: FastAPI 请求（从 request.state 取 trace_id 贯通链路）。
        code: 错误码。
        message: 用户可读的错误提示。

    Returns:
        JSONResponse: 401 + 统一信封。
    """
    return JSONResponse(
        status_code=401,
        content={
            "code": code,
            "message": message,
            "data": None,
            "traceId": getattr(request.state, "trace_id", ""),
        },
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
            # Java 可能返回 "00000" 或 200；响应须为 dict，否则按无效处理
            if not isinstance(data, dict):
                logger.warning("token/parse 响应非对象: %s", type(data).__name__)
                return None
            code = str(data.get("code", ""))
            if code not in ("00000", "200"):
                return None

            user_info = data.get("data")
            # data 字段须为 dict（含 userId 等），防御非对象响应
            return user_info if isinstance(user_info, dict) else None

    except (httpx.HTTPError, ValueError) as e:
        # ValueError: resp.json() 解析失败（Java 返回非 JSON）
        logger.error("token/parse 调用失败: %s", e)
        return None
