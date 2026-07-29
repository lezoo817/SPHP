"""Java 后端 HTTP 客户端。

封装对 C 端后端（8082）和 B 端后端（8081）的调用，
统一超时、重试、错误处理逻辑。
"""

from typing import Any

import httpx
from tenacity import retry, stop_after_attempt, wait_exponential

from app.infrastructure.config.settings import get_settings


@retry(
    stop=stop_after_attempt(3),
    wait=wait_exponential(multiplier=1, min=1, max=5),
)
def call_backend_api(
    method: str,
    path: str,
    scope: str = "c_end",
    params: dict | None = None,
    body: dict | None = None,
    token: str | None = None,
    timeout: int = 10,
) -> dict[str, Any]:
    """调用 Java 后端 API（带重试）。

    Args:
        method: HTTP 方法（GET / POST / PUT 等）。
        path: API 路径（如 /api/registration/slots）。
        scope: c_end（8082）或 b_end（8081）。
        params: URL 查询参数。
        body: JSON 请求体。
        token: JWT 认证令牌。
        timeout: 超时秒数。

    Returns:
        后端响应的 JSON dict。

    Raises:
        httpx.HTTPError: 3 次重试后仍失败。
    """
    settings = get_settings()
    base_url = settings.c_end_base_url if scope == "c_end" else settings.b_end_base_url
    url = base_url + path

    headers = {}
    if token:
        headers["Authorization"] = f"Bearer {token}"

    with httpx.Client(timeout=timeout) as client:
        if method == "GET":
            resp = client.get(url, params=params, headers=headers)
        else:
            resp = client.request(method, url, json=body, headers=headers)
        resp.raise_for_status()
        return resp.json()
