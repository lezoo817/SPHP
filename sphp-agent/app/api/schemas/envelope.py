"""统一 API 信封构造（系分 §6.1）。

所有对外 HTTP 接口统一返回 ``{code, message, data, traceId}`` 信封。
本模块集中构造成功/错误信封，消除各路由与中间件中重复的 JSONResponse 样板
（P3-3 DRY：chat.confirm / jwt_auth / rate_limit / knowledge 4 处收敛于此）。

刻意豁免的信封外契约（非缺陷，勿改）：
    - ``GET /health`` 健康探测：监控/编排系统直接解析顶层 status 字段，
      保持裸结构 ``{status, checks, version}``，不走统一信封（见 main.health）。
    - SSE 流式接口（/api/chat/stream）：事件流格式，非 JSON 信封（见 chat）。
"""

from typing import Any

from fastapi.responses import JSONResponse


def success_response(data: Any, trace_id: str, message: str = "success") -> JSONResponse:
    """构造成功统一信封（HTTP 200，code=00000）。

    Args:
        data: 业务数据负载（可为 dict / list / None）。
        trace_id: 链路追踪号（透传 request.state.trace_id）。
        message: 成功提示，默认 "success"。

    Returns:
        JSONResponse: 200 + {code: "00000", message, data, traceId}。
    """
    return JSONResponse(
        status_code=200,
        content={"code": "00000", "message": message, "data": data, "traceId": trace_id},
    )


def error_response(
    code: str,
    message: str,
    trace_id: str,
    status_code: int = 400,
) -> JSONResponse:
    """构造错误统一信封（系分 §6.1，data 恒为 None）。

    Args:
        code: 错误码（对齐系分 §6.1 错误码表，如 AUTH_INVALID / CONFIRM_EXPIRED）。
        message: 用户可读错误提示。
        trace_id: 链路追踪号。
        status_code: HTTP 状态码，默认 400；鉴权 401 / 限流 429 / 服务端 500
            等由调用方按错误语义传入。

    Returns:
        JSONResponse: 对应状态码 + {code, message, data: None, traceId}。
    """
    return JSONResponse(
        status_code=status_code,
        content={"code": code, "message": message, "data": None, "traceId": trace_id},
    )
