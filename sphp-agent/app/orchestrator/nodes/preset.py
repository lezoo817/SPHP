"""受控预设动作节点。

用于承接前端明确发起的低风险查询，避免由 LLM 决定是否调用指定工具。
"""

import logging
from typing import Any

from app.orchestrator.card_store import get_card_store
from app.orchestrator.state import AgentState

logger = logging.getLogger(__name__)

PRESET_INTERPRET_PRESCRIPTION = "interpret_prescription"
PRESET_INTERPRET_MEDICAL_RECORD = "interpret_medical_record"
PRESET_SELECT_PRESCRIPTION_INTERPRETATION = "select_prescription_interpretation"
PRESET_SELECT_MEDICAL_RECORD_INTERPRETATION = "select_medical_record_interpretation"
PRESET_RECOMMEND_PRESCRIPTION_PHARMACY = "recommend_prescription_pharmacy"
PRESET_NOTIFY_DRUG_ORDER_PAID = "notify_drug_order_paid"
PRESET_AUTHORIZE_DRUG_ORDER_REMINDER_AFTER_RECEIPT = "authorize_drug_order_reminder_after_receipt"


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


def resolve_preset_interpretation_picker(context: dict[str, Any] | None) -> str | None:
    """解析合法的病历或处方解读记录选择预设。

    Args:
        context: 前端随对话请求传入的页面上下文。

    Returns:
        合法的选择预设动作；非法值返回 None。
    """
    if not context:
        return None
    action = context.get("preset_action")
    return (
        action
        if action
        in {
            PRESET_SELECT_PRESCRIPTION_INTERPRETATION,
            PRESET_SELECT_MEDICAL_RECORD_INTERPRETATION,
        }
        else None
    )


def resolve_preset_medical_record_interpretation(
    context: dict[str, Any] | None,
) -> int | None:
    """解析合法的病历解读预设动作。

    Args:
        context: 前端随对话请求传入的页面上下文。

    Returns:
        合法病历 ID；不是指定预设动作或编号非法时返回 None。
    """
    if not context or context.get("preset_action") != PRESET_INTERPRET_MEDICAL_RECORD:
        return None
    consult_id = context.get("medical_record_id")
    # bool 是 int 的子类，需显式拒绝，避免 True 被错误当作病历 ID。
    if isinstance(consult_id, bool) or not isinstance(consult_id, int):
        return None
    return consult_id if consult_id > 0 else None


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


def resolve_preset_drug_order_reminder_authorization(
    context: dict[str, Any] | None,
) -> int | None:
    """解析合法的收货后自动提醒授权预设动作。

    Args:
        context: 前端随对话请求传入的页面上下文。

    Returns:
        合法购药订单 ID；不是指定预设动作或编号非法时返回 None。
    """
    if (
        not context
        or context.get("preset_action") != PRESET_AUTHORIZE_DRUG_ORDER_REMINDER_AFTER_RECEIPT
    ):
        return None
    drug_order_id = context.get("drug_order_id")
    # bool 是 int 的子类，需显式拒绝，避免 True 被错误当作订单 ID。
    if isinstance(drug_order_id, bool) or not isinstance(drug_order_id, int):
        return None
    return drug_order_id if drug_order_id > 0 else None


async def _mark_record_picker_selected(state: AgentState, record_id: int) -> None:
    """标记最近一张未选择的记录选择卡为已选（2026-08-10 增强）。

    用户从 record_picker 卡确认选择后，前端以 ``interpret_prescription`` /
    ``interpret_medical_record`` 预设请求回传 record_id。此处按事件类型把最新
    未标记的 record_picker 卡标记为已选，历史重放时显示"已选择"态而非初始态。
    未命中（无未标记记录选择卡，如详情页直接发起解读）返回 False，无副作用。

    Args:
        state: 当前 Agent 状态（含 user_id / session_id）。
        record_id: 用户选中的处方或病历记录 ID。
    """
    try:
        await get_card_store().mark_selected(
            state.get("user_id"),
            state.get("session_id"),
            "record_picker",
            {"record_id": record_id},
        )
    except Exception:
        logger.warning("标记记录选择卡已选失败: record_id=%s", record_id)


async def preset_action_node(state: AgentState) -> dict[str, Any]:
    """构造受控处方/病历解读、药店推荐、支付通知或提醒授权工具调用。

    Args:
        state: 已经完成鉴权的 Agent 状态。

    Returns:
        包含固定 L1 查询或待安全确认的 L2 授权调用；非法预设不产生调用。
    """
    action = state.get("preset_action")
    prescription_id = state.get("preset_prescription_id")
    if action in (
        PRESET_SELECT_PRESCRIPTION_INTERPRETATION,
        PRESET_SELECT_MEDICAL_RECORD_INTERPRETATION,
    ):
        # 快捷入口只读取当前就诊人最近 30 天记录，选择结果再进入既有受控解读预设。
        tool_name = (
            "query_prescriptions"
            if action == PRESET_SELECT_PRESCRIPTION_INTERPRETATION
            else "query_medical_records"
        )
        return {
            "tool_calls": [
                {
                    "name": tool_name,
                    "arguments": {
                        "patient_id": state.get("patient_id"),
                        "recent_days": 30,
                    },
                }
            ]
        }

    if action in (PRESET_INTERPRET_PRESCRIPTION, PRESET_RECOMMEND_PRESCRIPTION_PHARMACY):
        if (
            isinstance(prescription_id, bool)
            or not isinstance(prescription_id, int)
            or prescription_id <= 0
        ):
            return {"tool_calls": [], "preset_error": "处方编号无效，请返回处方详情后重试。"}

        if action == PRESET_INTERPRET_PRESCRIPTION:
            # 2026-08-10：用户从记录选择卡确认处方 -> 标记该卡为已选（历史重放已选态）
            await _mark_record_picker_selected(state, prescription_id)
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

    if action == PRESET_INTERPRET_MEDICAL_RECORD:
        consult_id = state.get("preset_medical_record_id")
        if isinstance(consult_id, bool) or not isinstance(consult_id, int) or consult_id <= 0:
            return {"tool_calls": [], "preset_error": "病历编号无效，请返回病历详情后重试。"}
        # 2026-08-10：用户从记录选择卡确认病历 -> 标记该卡为已选（历史重放已选态）
        await _mark_record_picker_selected(state, consult_id)
        # 受控入口仅读取病历和病历所属患者的健康档案，不开放任何病历修改操作。
        return {
            "tool_calls": [
                {
                    "name": PRESET_INTERPRET_MEDICAL_RECORD,
                    "arguments": {"consult_id": consult_id},
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

    if action == PRESET_AUTHORIZE_DRUG_ORDER_REMINDER_AFTER_RECEIPT:
        drug_order_id = state.get("preset_drug_order_id")
        if (
            isinstance(drug_order_id, bool)
            or not isinstance(drug_order_id, int)
            or drug_order_id <= 0
        ):
            return {
                "tool_calls": [],
                "preset_error": "购药订单编号无效，暂时无法设置自动用药提醒。",
            }
        # 用户点击入口后仍经过 L2 确认，不能由受控预设直接写入授权。
        return {
            "tool_calls": [
                {
                    "name": PRESET_AUTHORIZE_DRUG_ORDER_REMINDER_AFTER_RECEIPT,
                    "arguments": {"drug_order_id": drug_order_id},
                    "display": {
                        "title": "确认开启收货后用药提醒",
                        "summary": "订单确认收货后，将自动开启该订单药品的用药提醒。",
                        "details": {"drug_order_id": drug_order_id},
                    },
                }
            ]
        }

    return {"tool_calls": []}
