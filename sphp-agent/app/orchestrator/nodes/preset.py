"""受控预设动作节点。

用于承接前端明确发起的低风险查询，避免由 LLM 决定是否调用指定工具。
"""

from typing import Any

from app.orchestrator.state import AgentState

PRESET_INTERPRET_PRESCRIPTION = "interpret_prescription"
PRESET_RECOMMEND_PRESCRIPTION_PHARMACY = "recommend_prescription_pharmacy"
PRESET_NOTIFY_DRUG_ORDER_PAID = "notify_drug_order_paid"


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


def resolve_preset_recommendation(context: dict[str, Any] | None) -> int | None:
    """解析合法的处方药店推荐预设动作。

    Args:
        context: 前端随对话请求传入的页面上下文。

    Returns:
        合法处方 ID；不是指定预设动作或编号非法时返回 None。
    """
    if not context or context.get("preset_action") != PRESET_RECOMMEND_PRESCRIPTION_PHARMACY:
        return None
    prescription_id = context.get("prescription_id")
    # bool 是 int 的子类，需显式拒绝，避免 True 被错误当作处方 ID。
    if isinstance(prescription_id, bool) or not isinstance(prescription_id, int):
        return None
    return prescription_id if prescription_id > 0 else None


def resolve_preset_paid_order(context: dict[str, Any] | None) -> int | None:
    """解析合法的购药支付成功通知预设动作。

    Args:
        context: 前端随对话请求传入的页面上下文。

    Returns:
        合法购药订单 ID；不是指定预设动作或编号非法时返回 None。
    """
    if not context or context.get("preset_action") != PRESET_NOTIFY_DRUG_ORDER_PAID:
        return None
    drug_order_id = context.get("drug_order_id")
    # bool 是 int 的子类，需显式拒绝，避免 True 被错误当作订单 ID。
    if isinstance(drug_order_id, bool) or not isinstance(drug_order_id, int):
        return None
    return drug_order_id if drug_order_id > 0 else None


async def preset_action_node(state: AgentState) -> dict[str, Any]:
    """构造受控处方解读、药店推荐或支付通知工具调用。

    Args:
        state: 已经完成鉴权的 Agent 状态。

    Returns:
        仅包含指定 L1 工具调用的状态更新；非法预设不产生调用。
    """
    action = state.get("preset_action")
    prescription_id = state.get("preset_prescription_id")
    if action in (PRESET_INTERPRET_PRESCRIPTION, PRESET_RECOMMEND_PRESCRIPTION_PHARMACY):
        if (
            isinstance(prescription_id, bool)
            or not isinstance(prescription_id, int)
            or prescription_id <= 0
        ):
            return {"tool_calls": [], "preset_error": "处方编号无效，请返回处方详情后重试。"}

        if action == PRESET_INTERPRET_PRESCRIPTION:
            # 受控入口只允许读取既有解读，不开放任何创建、修改或处方操作。
            return {
                "tool_calls": [
                    {
                        "name": PRESET_INTERPRET_PRESCRIPTION,
                        "arguments": {"prescription_id": prescription_id},
                    }
                ]
            }

        address_id = state.get("address_id")
        if isinstance(address_id, bool) or not isinstance(address_id, int) or address_id <= 0:
            # 推荐必须基于地址计算距离与配送时效，缺失时不产生不可信推荐。
            return {
                "tool_calls": [],
                "preset_error": "请先在地址簿设置默认收货地址后，再为您推荐药店。",
            }
        return {
            "tool_calls": [
                {
                    "name": "recommend_pharmacies",
                    "arguments": {
                        "prescription_id": prescription_id,
                        "address_id": address_id,
                    },
                }
            ]
        }

    if action == PRESET_NOTIFY_DRUG_ORDER_PAID:
        drug_order_id = state.get("preset_drug_order_id")
        if (
            isinstance(drug_order_id, bool)
            or not isinstance(drug_order_id, int)
            or drug_order_id <= 0
        ):
            return {"tool_calls": [], "preset_error": "购药订单编号无效，暂时无法确认配送状态。"}
        # 支付通知只读取订单详情，支付动作始终由 C 端支付页完成。
        return {
            "tool_calls": [
                {
                    "name": "query_drug_orders",
                    "arguments": {"drug_order_id": drug_order_id},
                }
            ]
        }

    return {"tool_calls": []}
