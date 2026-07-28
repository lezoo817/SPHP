"""工具执行器：拦截 + 确认 + 调用 + 审计。

执行流程：
    1. LLM 输出 tool_call（工具名 + 参数）
    2. executor 查注册表 → 校验安全等级
    3. L3/L4 → 直接拒绝
    4. L2 且 requires_confirmation → 生成确认卡片，暂不执行
    5. L1 或已确认的 L2 → 调后端 API，记录审计日志
"""

import hashlib
import json
import time
from typing import Any

import httpx

from app.config.settings import get_settings
from app.tools.registry import SecurityLevel, ToolDef, get_tool


# ---- 暂存待确认的工具调用（内存版，后续可换 Redis） ----
_pending: dict[str, dict[str, Any]] = {}

# ---- 审计日志（内存版，后续落库） ----
_audit_log: list[dict[str, Any]] = []


def execute(tool_name: str, params: dict, user_context: dict, confirm_token: str | None = None) -> dict:
    """执行工具调用，返回结果或确认卡片。

    Args:
        tool_name: 工具名称
        params: LLM 传来的参数
        user_context: {user_id, role, session_id, scope}
        confirm_token: 二次确认凭证（用户点击确认后回传）

    Returns:
        {"status": "success", "data": ...}        # 执行成功
        {"status": "awaiting_confirmation", ...}   # 需要用户确认
        {"status": "error", "message": ...}        # 执行失败
    """
    tool = get_tool(tool_name)
    if tool is None:
        return {"status": "error", "message": f"工具不存在: {tool_name}"}

    # ---- L3/L4 硬拦截：Agent 无权调用 ----
    if tool.security_level in (SecurityLevel.L3, SecurityLevel.L4):
        return {"status": "error", "message": f"Agent 无权执行此操作: {tool_name}"}

    # ---- L2 需要二次确认 ----
    if tool.requires_confirmation:
        if confirm_token is None:
            # 首次调用：生成确认凭证，返回卡片
            token = _create_confirm_token(tool_name, params, user_context)
            return {
                "status": "awaiting_confirmation",
                "confirm_token": token,
                "card": _build_card(tool, params),
            }

        # 带了 token：校验
        pending = _pending.pop(confirm_token, None)
        if pending is None:
            return {"status": "error", "message": "确认凭证无效或已使用"}
        if time.time() > pending["expire_at"]:
            return {"status": "error", "message": "确认已过期，请重新操作"}
        if not _verify_params(pending["params"], params):
            return {"status": "error", "message": "参数不一致，请重新操作"}

    # ---- 放行：执行工具 ----
    if tool.api_method == "LOCAL":
        result = _call_local(tool, params)
    else:
        result = _call_backend(tool, params, user_context)

    # ---- 审计日志 ----
    _audit_log.append({
        "session_id": user_context.get("session_id"),
        "user_id": user_context.get("user_id"),
        "tool": tool_name,
        "params_hash": hashlib.sha256(json.dumps(params, sort_keys=True).encode()).hexdigest()[:16],
        "result": "success" if "error" not in result else "failed",
        "timestamp": time.time(),
    })

    return {"status": "success", "data": result}


# ---- 内部方法 ----

def _create_confirm_token(tool_name: str, params: dict, user_context: dict) -> str:
    """生成确认凭证（简易版，后续换 JWT）。"""
    token = hashlib.sha256(
        f"{tool_name}:{json.dumps(params, sort_keys=True)}:{user_context.get('session_id')}:{time.time()}"
        .encode()
    ).hexdigest()
    _pending[token] = {
        "tool_name": tool_name,
        "params": params,
        "user_id": user_context.get("user_id"),
        "expire_at": time.time() + 300,  # 5 分钟过期
    }
    return token


def _verify_params(stored: dict, incoming: dict) -> bool:
    """校验确认时的参数和暂存时是否一致（防偷换）。"""
    return hashlib.sha256(
        json.dumps(stored, sort_keys=True).encode()
    ).hexdigest() == hashlib.sha256(
        json.dumps(incoming, sort_keys=True).encode()
    ).hexdigest()


def _build_card(tool: ToolDef, params: dict) -> dict:
    """构建确认卡片数据，前端据此渲染。"""
    return {
        "title": f"确认操作: {tool.description}",
        "fields": [{"label": k, "value": str(v)} for k, v in params.items()],
        "actions": [
            {"label": "取消", "action": "cancel"},
            {"label": "确认", "action": "confirm"},
        ],
    }


def _call_backend(tool: ToolDef, params: dict, user_context: dict) -> dict:
    """调用 Java 后端 API。"""
    settings = get_settings()
    base_url = settings.c_end_base_url if tool.scope.value == "c_end" else settings.b_end_base_url
    url = base_url + tool.api_path

    headers = {}
    token = user_context.get("token")
    if token:
        headers["Authorization"] = f"Bearer {token}"

    try:
        with httpx.Client(timeout=10) as client:
            if tool.api_method == "GET":
                resp = client.get(url, params=params, headers=headers)
            else:
                resp = client.request(tool.api_method, url, json=params, headers=headers)
        resp.raise_for_status()
        return resp.json()
    except httpx.HTTPError as e:
        return {"error": f"后端调用失败: {e}"}


def _call_local(tool: ToolDef, params: dict) -> dict:
    """执行本地工具（不走 Java 后端，直接在 Python 内处理）。"""
    if tool.api_path == "knowledge.search":
        from app.knowledge.search import search_knowledge
        results = search_knowledge(query=params.get("query", ""))
        return {"results": results}

    return {"error": f"未知的本地工具: {tool.api_path}"}
