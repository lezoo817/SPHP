"""TraceId 注入中间件（系分 §5.8 审计追踪）。

为每个请求生成或透传 traceId，注入全链路日志。
"""

import uuid

from fastapi import Request
from starlette.middleware.base import BaseHTTPMiddleware, RequestResponseEndpoint
from starlette.responses import Response


class TracingMiddleware(BaseHTTPMiddleware):
    """注入 traceId 到 request.state 和响应头。"""

    async def dispatch(self, request: Request, call_next: RequestResponseEndpoint) -> Response:
        """请求入口：透传/生成 traceId，注入 request.state 与响应头。

        优先透传客户端 ``X-Trace-Id``，缺失时生成 UUID4；审计与统一信封
        均从 request.state.trace_id 读取，保证同请求全链路追踪号一致。
        """
        # 从请求头获取或生成 traceId
        trace_id = request.headers.get("X-Trace-Id") or str(uuid.uuid4())
        request.state.trace_id = trace_id

        response = await call_next(request)
        response.headers["X-Trace-Id"] = trace_id
        return response
