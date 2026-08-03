"""Java REST API 客户端（系分 §4.4 基础设施层）。

封装对 Java 后端（:8080）的 HTTP 调用，统一超时、错误处理、X-User-Id 注入。
替代原 backend/client.py，扁平化到 infrastructure 层根目录。
"""

import asyncio
import json
import logging
import uuid
from typing import Any, cast

import httpx

from app.infrastructure.config.settings import get_settings

# 接口契约表（Java 接口变化时改此表，见 java_api_map.py）
from app.infrastructure.java_api_map import resolve_api

logger = logging.getLogger(__name__)

# 全局异步客户端（连接池）
_client: httpx.AsyncClient | None = None

# P2 健壮性：瞬时故障（连接超时/拒绝、网关 5xx）指数退避重试
_MAX_RETRIES = 3  # 含首次尝试，共至多 3 次请求
_RETRY_BACKOFF = 0.3  # 退避基数（秒）：0.3 / 0.6 / 1.2
# 网关 5xx（上游抖动 / 网关错误）为瞬时故障，可安全重试
_GATEWAY_RETRYABLE = {502, 503, 504}
# 写入型方法：依赖 X-Idempotency-Key 去重，重试须复用同一键防重复执行业务
_WRITE_METHODS = {"POST", "PUT", "PATCH"}


class _RetryableJavaError(Exception):
    """瞬时故障（连接层超时/拒绝、网关 5xx），可安全重试。

    重试耗尽后由 call_java_api 捕获并返回统一失败信封。
    """


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


async def _do_request(
    client: httpx.AsyncClient,
    method: str,
    url: str,
    params: dict[str, Any] | None,
    body: dict[str, Any] | None,
    headers: dict[str, str],
) -> dict[str, Any]:
    """发起单次 Java HTTP 请求并解析响应（供重试循环调用）。

    与旧 call_java_api 的请求段逻辑等价，仅把"瞬时故障"分离为异常上抛，
    由重试循环决定是否重试；其余失败仍返回 ``success: False`` 信封。

    Args:
        client: 全局连接池客户端。
        method: HTTP 方法（GET/POST/PUT/PATCH/DELETE，已大写）。
        url: 完整请求 URL。
        params: URL 查询参数。
        body: JSON 请求体。
        headers: 请求头（含 X-User-Id / X-Idempotency-Key）。

    Returns:
        dict: 成功或确定失败的 Java 响应 dict（调用方直接返回）。

    Raises:
        _RetryableJavaError: 瞬时故障（连接超时/拒绝、网关 5xx），
            由 call_java_api 按指数退避重试。
    """
    try:
        resp = await client.request(method, url, params=params, json=body, headers=headers)
    except httpx.ConnectTimeout as e:
        logger.error("Java API connect timeout: %s %s", method, url)
        raise _RetryableJavaError from e
    except httpx.ConnectError as e:
        logger.error("Java API connect error: %s %s - %s", method, url, e)
        raise _RetryableJavaError from e
    except httpx.HTTPError as e:
        # 读超时等其他 HTTP 层错误：写操作 Java 可能已提交，不自动重试
        # （依靠 X-Idempotency-Key + 用户重试兜底），直接返回失败信封。
        logger.error("Java API error: %s %s - %s", method, url, e)
        return {
            "success": False,
            "error": {"code": "JAVA_5XX", "message": "服务异常，请稍后重试", "http_status": 0},
        }

    if resp.status_code >= 400:
        # 解析 Java 错误响应（系分 §5.3.2 三层错误传播）
        try:
            error_data = resp.json()
        except Exception:
            error_data = {}

        # 网关 5xx 为瞬时故障，可安全重试（GET 幂等 / 写入靠幂等键去重）
        if resp.status_code in _GATEWAY_RETRYABLE:
            logger.error("Java API gateway %d: %s %s", resp.status_code, method, url)
            raise _RetryableJavaError(f"gateway {resp.status_code}")

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
        return cast(dict[str, Any], resp.json())
    except json.JSONDecodeError:
        logger.error("Java API 响应非 JSON: %s %s", method, url)
        return {
            "success": False,
            "error": {
                "code": "JAVA_PARSE_ERROR",
                "message": "服务响应格式异常，请稍后重试",
                "http_status": resp.status_code,
            },
        }


async def call_java_api(
    method: str = "",
    path: str = "",
    *,
    tool_name: str | None = None,
    api_name: str | None = None,
    path_params: dict[str, Any] | None = None,
    params: dict[str, Any] | None = None,
    body: dict[str, Any] | None = None,
    user_id: int | None = None,
    idempotency_key: str | None = None,
) -> dict[str, Any]:
    """调用 Java REST API（系分 §6.4）。

    支持三种调用方式：
    1. 显式指定 method + path + params/body（兼容旧调用）
    2. tool_name 查表：从 ``java_api_map`` 契约表解析 method/path/scope
       （工具文件改传 tool_name，Java 接口变化只改契约表）
    3. api_name + path_params 查表：供聚合接口内部调用具体子接口

    P2 健壮性（2026-08-03）：
        - **瞬时故障重试**：连接超时/连接拒绝/网关 5xx（502/503/504）按指数
          退避重试至多 3 次，缓解 Java 瞬时抖动直接失败（原实现单次请求）。
        - **幂等键复用**：幂等键在本次调用内生成一次，重试全程复用同一键，
          Java 侧按 ``X-Idempotency-Key`` 去重——网络重试不会重复执行业务
          （挂号锁定、购药下单等 L2 写操作）。调用方也可显式传入复用键
          （同一业务多次调用共用），如 confirm 流程按 confirm_token 派生。

    Args:
        method: HTTP 方法（GET/POST/PUT/PATCH/DELETE）
        path: API 路径（如 /api/c/v1/departments）
        tool_name: 工具名（查契约表确定 API 路径，method/path 为空时使用）
        api_name: 语义接口名（查契约表，path 含 {param} 时与 path_params 配合）
        path_params: 路径参数 {参数名: 值}，替换契约表 path 中的 {param}
        params: URL 查询参数
        body: JSON 请求体
        user_id: 用户ID（注入 X-User-Id Header 做数据隔离）
        idempotency_key: 幂等键（P2）。重试同一业务操作时传入复用同一键；
            None 时本次调用生成唯一键并在重试间复用。

    Returns:
        正常返回 Java 响应 dict；失败（重试耗尽 / 非 2xx / 非 JSON）返回含
        ``error`` 字段的 dict，不抛异常。
    """
    settings = get_settings()
    client = await get_client()

    # 方式 2/3：从契约表解析 method/path（scope 由契约表保证，前缀统一走 java_base_url）
    if tool_name or api_name:
        api = api_name or tool_name or ""
        try:
            resolved_method, resolved_path, _resolved_scope = resolve_api(api, path_params)
        except KeyError as e:
            logger.error("接口契约解析失败: %s", e)
            return {
                "success": False,
                "error": {"code": "API_CONTRACT_ERROR", "message": f"接口未定义: {api}"},
            }
        method = method or resolved_method
        path = resolved_path

    url = f"{settings.java_base_url}{path}"

    headers = {"Content-Type": "application/json"}
    if user_id is not None:
        headers["X-User-Id"] = str(user_id)

    method_upper = method.upper()
    # 幂等键：调用方显式传入或本调用生成一次；重试循环全程复用同一键，
    # 保证 Java 侧去重，网络重试不重复执行业务。
    if method_upper in _WRITE_METHODS:
        headers["X-Idempotency-Key"] = idempotency_key or str(uuid.uuid4())

    # 重试循环：仅 _RetryableJavaError（瞬时故障）触发重试，其余失败直接返回
    attempt = 0
    while True:
        try:
            return await _do_request(client, method_upper, url, params, body, headers)
        except _RetryableJavaError:
            attempt += 1
            if attempt >= _MAX_RETRIES:
                logger.error("Java 调用重试耗尽（%d 次）: %s %s", attempt, method_upper, path)
                return {
                    "success": False,
                    "error": {
                        "code": "JAVA_TIMEOUT",
                        "message": "系统繁忙，请稍后重试",
                        "http_status": 0,
                    },
                }
            delay = _RETRY_BACKOFF * (2 ** (attempt - 1))
            logger.warning(
                "Java 调用瞬时故障，%.1fs 后重试（%d/%d）: %s %s",
                delay,
                attempt + 1,
                _MAX_RETRIES,
                method_upper,
                path,
            )
            await asyncio.sleep(delay)
