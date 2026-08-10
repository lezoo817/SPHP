"""安全校验节点（系分 §5.4 L1-L4 体系）。

检查 tool_calls 中的工具安全等级：
- L1：直接放行
- L2：生成 confirm_token 存入 Redis，挂起等待用户确认
- L3/L4：直接拒绝（不应出现，因为不注册）
"""

import logging
import time
import uuid
from typing import Any

from app.engine.tools.schema_registry import SecurityLevel, ToolRegistry
from app.infrastructure.cache.redis_client import get_confirm_token_by_token, set_confirm_token
from app.infrastructure.config.settings import get_settings
from app.orchestrator.state import AgentState

logger = logging.getLogger(__name__)


async def safety_check(state: AgentState) -> dict[str, Any]:
    """检查 tool_calls 中的工具等级，L1 直接放行，L2 生成 confirm_token 存 Redis。

    安全护栏：Redis 不可用时**剔除**该 L2 调用（而非只打标记），确保
    ``tool_executor`` 不会无确认执行敏感操作；L3/L4 同理剔除。
    """
    tool_calls = state.get("tool_calls") or []
    if not tool_calls:
        return {}

    risk_flags = list(state.get("risk_flags", []))
    # P1-2 自累积：保留子图循环前面轮次已挂起的 L2 确认（pending_confirmations
    # 无 reducer，默认 last-write-wins 会覆盖丢失先触发的卡片）。与 tool_results
    # 的自累积对齐（见 tool_executor）：跨轮对话由 _build_initial_state 传 None
    # （last-write-wins）正常清场，历史卡片不会污染下一轮。
    pending_confirmations = list(state.get("pending_confirmations") or [])
    # 放行的工具调用（过滤掉 Redis 失败的 L2 / 未授权的 L3/L4）
    allowed_calls: list[dict[str, Any]] = []

    for tc in tool_calls:
        tool_name = tc.get("name", "")
        tool = ToolRegistry.get_tool(tool_name)
        if tool is None:
            continue

        if tool.security_level == SecurityLevel.L2:
            tool_arguments = tc.get("arguments", {})
            display = tc.get("display")
            session_id = state.get("session_id") or ""
            user_id = str(state.get("user_id") or "")

            # M8-1：查找 pending 是否已有相同 tool_name+args 的项（防重复挂起）。
            # 子图循环第二轮 LLM 重复返回同一 L2 时，_dedupe_tool_calls 已在源头
            # 拦截；此处为确定性兜底-即使漏过，也复用已有 token 而非生成新的，
            # 确保同一 L2 恰好一张确认卡。
            existing = _find_pending(pending_confirmations, tool_name, tool_arguments)
            if existing is not None:
                existing_token = existing.get("confirm_token", "")
                try:
                    record = await get_confirm_token_by_token(session_id, existing_token, user_id)
                except Exception:
                    # Redis 故障：无法校验有效性，保守按失效处理，清理旧项重新生成
                    record = None
                if record is not None:
                    # token 仍有效（未确认未过期）：复用，不重复挂起
                    logger.info("复用已有 confirm_token: %s %s", tool_name, tool_arguments)
                    continue
                # token 已失效（前卡已确认/过期）：清理旧 pending 项后重新生成
                pending_confirmations = [p for p in pending_confirmations if p is not existing]
                logger.info("旧 confirm_token 已失效，重新生成: %s", tool_name)

            # 生成 confirm_token，写入 Redis（5min TTL），暂存待确认
            token = str(uuid.uuid4())
            try:
                await set_confirm_token(
                    token_id=token,
                    session_id=session_id,
                    user_id=user_id,
                    tool_name=tool_name,
                    tool_arguments=tool_arguments,
                    card_type=_map_card_type(tool_name),
                )
            except Exception as e:
                # Redis 不可用：拒绝该 L2 操作并剔除调用，绝不让 tool_executor
                # 绕过确认直接执行（系分 §5.5 安全护栏）
                logger.error("L2 工具 %s 生成 confirm_token 失败，拒绝执行: %s", tool_name, e)
                risk_flags.append(f"redis_unavailable_{tool_name}")
                continue

            # 过期时间（ISO 格式），与 Redis TTL 一致，供 card 事件展示倒计时
            expires_at = time.strftime(
                "%Y-%m-%dT%H:%M:%S", time.localtime(time.time() + get_settings().confirm_token_ttl)
            )
            pending_confirmations.append(
                {
                    "tool_name": tool_name,
                    "tool_arguments": tool_arguments,
                    "confirm_token": token,
                    "card_type": _map_card_type(tool_name),
                    "session_id": session_id,
                    "expires_at": expires_at,
                    # 展示信息只用于前端确认卡，不参与 MCP 执行或 Redis 幂等比对。
                    "display": display if isinstance(display, dict) else None,
                }
            )
            # L2 工具不直接执行（等确认），也不放入 allowed_calls
        elif tool.security_level in (SecurityLevel.L3, SecurityLevel.L4):
            # L3/L4 未授权：剔除调用，纵深防御（tool_caller 过滤的兜底）
            logger.warning("拦截未授权工具调用: %s", tool_name)
            risk_flags.append(f"blocked_{tool_name}")
        else:
            # L1 查询工具：直接放行执行
            allowed_calls.append(tc)

    result: dict[str, Any] = {"risk_flags": risk_flags}
    # 总是回写过滤后的 tool_calls（L1 工具），让 tool_executor 立即执行
    # 同时 pending_confirmations 全量写入，供 route_safety 判断路由
    result["tool_calls"] = allowed_calls
    if pending_confirmations:
        result["pending_confirmations"] = pending_confirmations
    return result


def _map_card_type(tool_name: str) -> str:
    """工具名到 card_type 的映射（系分 §6.2.2 card_type 枚举）。"""
    mapping = {
        "create_appointment": "confirm_appointment",
        "cancel_appointment": "confirm_cancel_appointment",
        "save_pre_consultation": "confirm_pre_consultation",
        "send_consultation_message": "confirm_send_message",
        "create_drug_order": "confirm_drug_order",
        "authorize_drug_order_reminder_after_receipt": "confirm_drug_order_reminder_after_receipt",
        "cancel_drug_order": "confirm_cancel_drug_order",
        "confirm_drug_receipt": "confirm_drug_receipt",
        "join_waitlist": "confirm_waitlist",
        "manage_allergy": "confirm_allergy",
        "manage_medical_history": "confirm_medical_history",
        "delete_allergy": "confirm_allergy",
        "delete_medical_history": "confirm_medical_history",
        "create_report": "confirm_report",
        "update_medication_plan": "confirm_medication_plan",
        "confirm_follow_up": "confirm_follow_up",
        "generate_draft_note": "confirm_draft_note",
        "query_patient_history": "confirm_patient_history",
    }
    return mapping.get(tool_name, "confirm_generic")


def _find_pending(
    pending: list[dict[str, Any]], tool_name: str, tool_arguments: dict[str, Any]
) -> dict[str, Any] | None:
    """查找 pending 列表中相同 tool_name+tool_arguments 的项（M8-1 复用 token）。

    Args:
        pending: ``pending_confirmations`` 列表。
        tool_name: 待查工具名。
        tool_arguments: 待查工具参数。

    Returns:
        匹配的 pending 项 dict；无匹配返回 None。
    """
    for p in pending:
        if p.get("tool_name") == tool_name and (p.get("tool_arguments") or {}) == tool_arguments:
            return p
    return None
