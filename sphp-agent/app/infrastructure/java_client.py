"""Java REST API 客户端（系分 §4.4 基础设施层）。

封装对 Java 后端（:8080）的 HTTP 调用，统一超时、错误处理、X-User-Id 注入。
替代原 backend/client.py，扁平化到 infrastructure 层根目录。
"""

import json
import logging
import uuid
from typing import Any

import httpx

from app.infrastructure.config.settings import get_settings

logger = logging.getLogger(__name__)

# 全局异步客户端（连接池）
_client: httpx.AsyncClient | None = None


async def get_client() -> httpx.AsyncClient:
    """获取全局 httpx AsyncClient（单例，连接池复用）。

    连接池配置（系分 §10.4）：limits=100, keepalive=30s
    """
    global _client
    if _client is None or _client.is_closed:
        _client = httpx.AsyncClient(
            limits=httpx.Limits(max_connections=100, max_keepalive_connections=30),
            timeout=httpx.Timeout(10.0, connect=5.0),
        )
    return _client


async def close_client() -> None:
    """关闭连接池（优雅关闭时调用）。"""
    global _client
    if _client and not _client.is_closed:
        await _client.aclose()
        _client = None


async def call_java_api(
    method: str = "",
    path: str = "",
    *,
    tool_name: str | None = None,
    arguments: dict | None = None,
    params: dict | None = None,
    body: dict | None = None,
    user_id: int | None = None,
    scope: str = "c_end",
) -> dict[str, Any]:
    """调用 Java REST API（系分 §6.4）。

    支持两种调用方式：
    1. 显式指定 method + path + params/body（MCP Server 工具直接调用）
    2. 传入 tool_name + arguments（由编排层 tool_executor 调用，需查表确定 API 路径）

    Args:
        method: HTTP 方法（GET/POST/PUT/PATCH/DELETE）
        path: API 路径（如 /api/c/v1/departments）
        tool_name: 工具名（用于查表确定 API 路径，当 method/path 为空时使用）
        arguments: 工具参数（当使用 tool_name 时传入）
        params: URL 查询参数
        body: JSON 请求体
        user_id: 用户ID（注入 X-User-Id Header 做数据隔离）
        scope: c_end / b_end（决定 API 前缀）

    Returns:
        正常返回 Java 响应 dict；失败（超时 / 非 2xx / 非 JSON）返回含
        ``error`` 字段的 dict，不抛异常。
    """
    settings = get_settings()
    client = await get_client()

    # 如果传了 tool_name 但没传 method/path，从参数推导
    # 当前实现：MCP Server 工具文件直接传 method+path
    if not method and tool_name:
        raise NotImplementedError(f"tool_name 分发未实现，请显式传入 method + path: {tool_name}")

    url = f"{settings.java_base_url}{path}"

    headers = {"Content-Type": "application/json"}
    if user_id is not None:
        headers["X-User-Id"] = str(user_id)

    # 创建型操作生成幂等键
    if method.upper() in ("POST", "PUT", "PATCH"):
        headers["X-Idempotency-Key"] = str(uuid.uuid4())

    try:
        resp = await client.request(
            method.upper(),
            url,
            params=params,
            json=body,
            headers=headers,
        )

        if resp.status_code >= 400:
            # 解析 Java 错误响应（系分 §5.3.2 三层错误传播）
            try:
                error_data = resp.json()
            except Exception:
                error_data = {}

            return {
                "success": False,
                "error": {
                    "code": error_data.get("code", f"JAVA_{resp.status_code}"),
                    "message": error_data.get("message", "服务异常，请稍后重试"),
                    "detail": error_data.get("detail", ""),
                    "http_status": resp.status_code,
                },
            }

        try:
            return resp.json()
        except json.JSONDecodeError:
            logger.error("Java API 响应非 JSON: %s %s", method, path)
            return {
                "success": False,
                "error": {
                    "code": "JAVA_PARSE_ERROR",
                    "message": "服务响应格式异常，请稍后重试",
                    "http_status": resp.status_code,
                },
            }

    except httpx.ConnectTimeout:
        logger.error("Java API connect timeout: %s %s", method, path)
        return {
            "success": False,
            "error": {"code": "JAVA_TIMEOUT", "message": "系统繁忙，请稍后重试", "http_status": 0},
        }
    except httpx.HTTPError as e:
        logger.error("Java API error: %s %s - %s", method, path, e)
        return {
            "success": False,
            "error": {"code": "JAVA_5XX", "message": "服务异常，请稍后重试", "http_status": 0},
        }
