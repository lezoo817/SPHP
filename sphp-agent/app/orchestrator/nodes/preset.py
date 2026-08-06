"""受控预设动作节点。

用于承接前端明确发起的低风险查询，避免由 LLM 决定是否调用指定工具。
"""

from typing import Any

from app.orchestrator.state import AgentState

PRESET_INTERPRET_PRESCRIPTION = "interpret_prescription"


def resolve_preset_interpretation(context: dict[str, Any] | None) -> int | None:
    """解析合法的处方解读预设动作。

    Args:
        context: 前端随对话请求传入的页面上下文。

    Returns:
        合法处方 ID；不是指定预设动作或编号非法时返回 None。
    """
    if not context or context.get("preset_action") != PRESET_INTERPRET_PRESCRIPTION:
        return None
    prescription_id = context.get("prescription_id")
    # bool 是 int 的子类，需显式拒绝，避免 True 被错误当作处方 ID。
    if isinstance(prescription_id, bool) or not isinstance(prescription_id, int):
        return None
    return prescription_id if prescription_id > 0 else None


async def preset_action_node(state: AgentState) -> dict[str, Any]:
    """构造受控处方解读工具调用。

    Args:
        state: 已经完成鉴权的 Agent 状态。

    Returns:
        仅包含指定 L1 工具调用的状态更新；非法预设不产生调用。
    """
    prescription_id = state.get("preset_prescription_id")
    if state.get("preset_action") != PRESET_INTERPRET_PRESCRIPTION:
        return {"tool_calls": []}
    if (
        isinstance(prescription_id, bool)
        or not isinstance(prescription_id, int)
        or prescription_id <= 0
    ):
        return {"tool_calls": []}

    # 受控入口只允许读取既有解读，不开放任何创建、修改或处方操作。
    return {
        "tool_calls": [
            {
                "name": PRESET_INTERPRET_PRESCRIPTION,
                "arguments": {"prescription_id": prescription_id},
            }
        ]
    }
