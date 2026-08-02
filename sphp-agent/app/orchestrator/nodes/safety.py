"""安全校验节点（系分 §5.4 L1-L4 体系）。

检查 tool_calls 中的工具安全等级：
- L1：直接放行
- L2：生成 confirm_token，挂起等待用户确认
- L3/L4：直接拒绝（不应出现，因为不注册）
"""

import uuid

from app.engine.tools.schema_registry import SecurityLevel, ToolRegistry
from app.orchestrator.state import AgentState


async def safety_check(state: AgentState) -> dict:
    """检查 tool_calls 中的工具等级，L1 直接放行，L2 生成 confirm_token。"""
    tool_calls = state.get("tool_calls") or []
    if not tool_calls:
        return {}

    risk_flags = list(state.get("risk_flags", []))
    pending_confirmations = []

    for tc in tool_calls:
        tool_name = tc.get("name", "")
        tool = ToolRegistry.get_tool(tool_name)
        if tool is None:
            continue

        if tool.security_level == SecurityLevel.L2:
            # 生成 confirm_token，暂存待确认
            token = str(uuid.uuid4())
            pending_confirmations.append({
                "tool_name": tool_name,
                "tool_arguments": tc.get("arguments", {}),
                "confirm_token": token,
                "card_type": _map_card_type(tool_name),
            })
        elif tool.security_level in (SecurityLevel.L3, SecurityLevel.L4):
            risk_flags.append(f"blocked_{tool_name}")

    if pending_confirmations:
        # 只处理第一个待确认的操作
        return {"pending_confirmation": pending_confirmations[0], "risk_flags": risk_flags}

    return {"risk_flags": risk_flags}


def _map_card_type(tool_name: str) -> str:
    """工具名到 card_type 的映射（系分 §6.2.2 card_type 枚举）。"""
    mapping = {
        "create_appointment": "confirm_appointment",
        "cancel_appointment": "confirm_cancel_appointment",
        "save_pre_consultation": "confirm_pre_consultation",
        "send_consultation_message": "confirm_send_message",
        "create_drug_order": "confirm_drug_order",
        "cancel_drug_order": "confirm_cancel_drug_order",
        "manage_allergy": "confirm_allergy",
        "manage_medical_history": "confirm_medical_history",
        "create_report": "confirm_report",
        "update_medication_plan": "confirm_medication_plan",
        "confirm_follow_up": "confirm_follow_up",
        "generate_draft_note": "confirm_draft_note",
    }
    return mapping.get(tool_name, "confirm_generic")
