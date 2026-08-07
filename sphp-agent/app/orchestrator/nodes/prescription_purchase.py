"""处方推荐药店后的确定性购药确认编排。"""

from datetime import datetime, timedelta
from typing import Any

from app.orchestrator.state import AgentState


def _response_payload(result: dict[str, Any]) -> Any:
    """提取 MCP 工具返回的 Java 业务数据。

    Args:
        result: 标准工具执行结果。

    Returns:
        Java 成功信封内的 data；结构不符合预期时返回 None。
    """
    response = result.get("data")
    if isinstance(response, dict) and "data" in response:
        return response.get("data")
    return response


def _positive_int(value: Any) -> int | None:
    """校验业务 ID 是否为正整数。

    Args:
        value: 待校验值。

    Returns:
        正整数；非法值返回 None。
    """
    if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
        return None
    return value


def _format_estimated_delivery_at(delivery_minutes: Any) -> str | None:
    """将 Java 推荐的配送分钟数转换为具体预计送达时间。

    Args:
        delivery_minutes: Java 配送推荐返回的预计分钟数。

    Returns:
        以服务器当前时间为起点的预计送达时间；参数非法时返回 None。
    """
    if isinstance(delivery_minutes, bool) or not isinstance(delivery_minutes, int):
        return None
    if delivery_minutes < 0:
        return None
    # 订单确认紧随推荐卡，服务端时间比浏览器时间更可靠且避免客户端时区被伪造。
    expected_at = datetime.now().astimezone() + timedelta(minutes=delivery_minutes)
    return expected_at.strftime("%Y/%m/%d %H:%M")


def _recommendation_result(state: AgentState) -> tuple[dict[str, Any] | None, str | None]:
    """读取本轮药店推荐结果中的第一项或真实失败提示。

    Args:
        state: 含 recommend_pharmacies 执行结果的 Agent 状态。

    Returns:
        ``(第一条药店推荐, 失败提示)``；空候选两项均为 None。
    """
    for result in state.get("tool_results") or []:
        if result.get("tool_name") != "recommend_pharmacies":
            continue
        if not result.get("success"):
            error = result.get("error")
            message = error.get("message") if isinstance(error, dict) else None
            return None, message or "药店推荐服务暂时不可用，请稍后重试。"
        payload = _response_payload(result)
        if isinstance(payload, list) and payload and isinstance(payload[0], dict):
            return payload[0], None
        return None, None
    return None, "药店推荐服务暂时不可用，请稍后重试。"


async def prepare_recommended_drug_order(state: AgentState) -> dict[str, Any]:
    """把排序第一的有货药店转为待确认的 L2 下单调用。

    该节点只在受控推荐预设完成 L1 查询后执行。它不调用模型，确保选中药房、
    下单参数和后续确认卡都来自 Java 返回的真实推荐结果。

    Args:
        state: 当前 Agent 状态，含处方、地址和药店推荐工具结果。

    Returns:
        L2 create_drug_order 调用或可展示的推荐失败提示。
    """
    recommendation, error_message = _recommendation_result(state)
    if error_message:
        # Java 的无权、处方状态和服务异常都应原样保留，不能伪装成库存为空。
        return {"tool_calls": [], "preset_error": error_message}
    if recommendation is None:
        return {
            "tool_calls": [],
            "preset_error": "暂未找到可配送且处方药品均有货的药店，请稍后再试。",
        }

    prescription_id = _positive_int(state.get("preset_prescription_id"))
    address_id = _positive_int(state.get("address_id"))
    pharmacy_id = _positive_int(recommendation.get("pharmacyId"))
    if prescription_id is None or address_id is None or pharmacy_id is None:
        # 业务 ID 不完整时拒绝构造下单请求，避免将不可信数据送入确认令牌。
        return {"tool_calls": [], "preset_error": "药店推荐信息不完整，暂时无法创建购药确认。"}

    pharmacy_name = str(recommendation.get("name") or "推荐药店")
    total_amount = recommendation.get("totalAmountCent")
    distance_meters = recommendation.get("distanceMeters")
    expected_delivery_at = _format_estimated_delivery_at(
        recommendation.get("estimatedDeliveryMinutes")
    )
    details = {
        "pharmacy_name": pharmacy_name,
        "total_amount_cent": total_amount,
        "distance_meters": distance_meters,
        "estimated_delivery_at": expected_delivery_at,
    }
    details = {key: value for key, value in details.items() if value is not None}

    # display 仅用于确认卡展示，安全令牌仍只保存 Java 下单所需的业务 ID。
    return {
        "tool_calls": [
            {
                "name": "create_drug_order",
                "arguments": {
                    "prescription_id": prescription_id,
                    "pharmacy_id": pharmacy_id,
                    "address_id": address_id,
                },
                "display": {
                    "title": "是否确认购买",
                    "summary": f"将从{pharmacy_name}购买处方药品，请确认订单信息。",
                    "details": details,
                },
            }
        ]
    }
