"""回复生成节点（系分 §5.12）。

汇总所有上游节点输出，调用 LLM 生成自然语言回复。
"""

import json
import logging
from datetime import date, datetime
from typing import Any

from app.engine.llm.factory import build_llm
from app.engine.memory.buffer import truncate_messages
from app.infrastructure.config.settings import get_settings
from app.orchestrator.state import AgentState

logger = logging.getLogger(__name__)

# 回复生成系统提示词
REPLY_SYSTEM_PROMPT = """你是一个医疗健康助手，正在为用户提供服务。

当前日期：{today}（服务器本地日期，YYYY-MM-DD）

当前场景：
- 用户意图：{intent}
- 服务端：{scope}

任务：
1. 根据对话历史和工具调用结果，生成自然、友好的回复
2. 如果有工具调用结果，优先基于结果回答
3. 保持回复简洁、专业，避免过度医疗建议
4. 涉及诊断、用药建议时，提醒用户咨询专业医生
5. 涉及日期/排班信息时，以当前日期 {today} 为参照，不得混淆或编造日期

注意：
- 不要编造医疗数据
- 不要替代医生做诊断
- 不确定的内容要明确告知
- **输出必须是纯文本**：不得使用 Markdown 星号（**加粗**、*斜体*、列表项 - / *）、
  井号标题、反引号等标记语法。需要强调的内容用自然语言表述即可。
"""

# 处方解读来源标识，与 MCP 处方工具返回的数据契约一致。
_OFFICIAL_READY_SOURCE = "OFFICIAL_READY"
_AI_FALLBACK_SOURCE = "AI_FALLBACK"
_PRESET_INTERPRET_PRESCRIPTION = "interpret_prescription"
_PRESET_INTERPRET_MEDICAL_RECORD = "interpret_medical_record"
_PRESET_SELECT_PRESCRIPTION_INTERPRETATION = "select_prescription_interpretation"
_PRESET_SELECT_MEDICAL_RECORD_INTERPRETATION = "select_medical_record_interpretation"
_PRESET_RECOMMEND_PRESCRIPTION_PHARMACY = "recommend_prescription_pharmacy"
_PRESET_NOTIFY_DRUG_ORDER_PAID = "notify_drug_order_paid"
_PRESET_AUTHORIZE_DRUG_ORDER_REMINDER_AFTER_RECEIPT = "authorize_drug_order_reminder_after_receipt"


def _positive_int(value: Any) -> int | None:
    """校验正整数业务编号。

    Args:
        value: 待校验值。

    Returns:
        合法正整数；非法值返回 None。
    """
    if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
        return None
    return value


def _interpretation_prescription_id(state: AgentState) -> int | None:
    """读取本轮成功处方解读对应的处方 ID。

    Args:
        state: 当前 Agent 状态。

    Returns:
        合法处方 ID；无法确认时返回 None。
    """
    preset_id = _positive_int(state.get("preset_prescription_id"))
    if preset_id is not None:
        return preset_id
    for result in state.get("tool_results") or []:
        if result.get("tool_name") != "interpret_prescription" or not result.get("success"):
            continue
        arguments = result.get("arguments")
        if isinstance(arguments, dict):
            return _positive_int(arguments.get("prescription_id"))
    return None


def _interpretation_action_cards(
    state: AgentState,
) -> tuple[list[dict[str, Any]] | None, str | None]:
    """按处方解读结果构造药店推荐入口或无地址提示。

    Args:
        state: 当前 Agent 状态。

    Returns:
        交互卡列表及可追加到解读后的提示文本。
    """
    prescription_id = _interpretation_prescription_id(state)
    if prescription_id is None:
        return None, None
    address_id = _positive_int(state.get("address_id"))
    if address_id is None:
        return None, "请先在地址簿设置默认收货地址后，再为您推荐药店。"
    return (
        [
            {
                "action_type": _PRESET_RECOMMEND_PRESCRIPTION_PHARMACY,
                "title": "药店推荐",
                "summary": "需要我为您推荐相关药店吗？",
                "button_text": "推荐药店",
                "arguments": {"prescription_id": prescription_id},
            }
        ],
        None,
    )


def _response_payload(result: dict[str, Any]) -> Any:
    """提取标准工具结果中的 Java 业务数据。

    Args:
        result: MCP 工具执行结果。

    Returns:
        Java 响应 data；结构不符合预期时返回原始数据。
    """
    response = result.get("data")
    if isinstance(response, dict) and "data" in response:
        return response.get("data")
    return response


def _build_interpretation_record_picker(state: AgentState) -> dict[str, Any] | None:
    """将最近病历或处方查询结果转换为受控选择卡。

    Args:
        state: 含受控列表查询结果的当前 Agent 状态。

    Returns:
        选择卡与提示消息；查询失败时返回原始失败说明；非选择预设时返回 None。
    """
    action = state.get("preset_action")
    if action not in {
        _PRESET_SELECT_PRESCRIPTION_INTERPRETATION,
        _PRESET_SELECT_MEDICAL_RECORD_INTERPRETATION,
    }:
        return None
    tool_name = (
        "query_prescriptions"
        if action == _PRESET_SELECT_PRESCRIPTION_INTERPRETATION
        else "query_medical_records"
    )
    for result in state.get("tool_results") or []:
        if result.get("tool_name") != tool_name:
            continue
        if not result.get("success"):
            error = result.get("error")
            message = error.get("message") if isinstance(error, dict) else None
            return {
                "messages": [
                    {"role": "assistant", "content": message or "记录查询失败，请稍后重试。"}
                ]
            }
        payload = _response_payload(result)
        records = payload.get("records") if isinstance(payload, dict) else None
        if not isinstance(records, list) or not records:
            record_name = "处方" if tool_name == "query_prescriptions" else "病历"
            return {
                "messages": [
                    {"role": "assistant", "content": f"最近 30 天暂无可解读的{record_name}。"}
                ]
            }
        items: list[dict[str, Any]] = []
        for record in records:
            if not isinstance(record, dict):
                continue
            record_id = _positive_int(record.get("id"))
            if record_id is None:
                continue
            if tool_name == "query_prescriptions":
                title = record.get("displayName") or "处方药品"
                date_text = record.get("issuedAt") or "开方时间待确认"
                doctor = record.get("doctorName") or "医生待确认"
                description = f"{doctor} · {date_text}"
                picker_type = "prescription"
            else:
                department = record.get("departmentName") or "就诊"
                doctor = record.get("doctorName") or "医生"
                title = f"{department} · {doctor}病历"
                description = str(record.get("completedAt") or "完成时间待确认")
                picker_type = "medical_record"
            items.append({"id": record_id, "title": str(title), "description": description})
        if not items:
            return {"messages": [{"role": "assistant", "content": "最近 30 天暂无可解读的记录。"}]}
        label = "处方" if picker_type == "prescription" else "病历"
        return {
            "messages": [
                {"role": "assistant", "content": f"请从最近 30 天记录中选择需要解读的{label}。"}
            ],
            "record_pickers": [{
                "picker_type": picker_type,
                "title": f"请选择要解读的{label}",
                "confirm_text": "确认",
                "cancel_text": "取消",
                "items": items,
            }],
        }
    return None


def _extract_medical_record_interpretation(
    tool_results: list[dict[str, Any]] | None,
) -> dict[str, Any] | None:
    """提取受控病历解读工具返回的病历与健康档案信息。

    Args:
        tool_results: 本轮 MCP 工具执行结果。

    Returns:
        含 medical_record 和 health_record 的解读数据；无有效结果时返回 None。
    """
    for result in tool_results or []:
        if result.get("tool_name") != _PRESET_INTERPRET_MEDICAL_RECORD:
            continue
        if not result.get("success"):
            return None
        payload = _response_payload(result)
        if not isinstance(payload, dict):
            return None
        medical_record = payload.get("medical_record")
        health_record = payload.get("health_record")
        if isinstance(medical_record, dict) and isinstance(health_record, dict):
            return payload
    return None


def _build_medical_record_interpretation_context(
    interpretation: dict[str, Any],
) -> str | None:
    """构造病历与健康档案解读的受限 LLM 输入。

    Args:
        interpretation: 受控病历解读工具成功返回的结构化数据。

    Returns:
        限制模型解读范围、格式和医疗安全边界的系统提示；数据异常时返回 None。
    """
    medical_record = interpretation.get("medical_record")
    health_record = interpretation.get("health_record")
    if not isinstance(medical_record, dict) or not isinstance(health_record, dict):
        return None
    try:
        detail = json.dumps(medical_record, ensure_ascii=False)
        health = json.dumps(health_record, ensure_ascii=False)
    except (TypeError, ValueError):
        return None
    return (
        "当前回复必须且只能基于以下真实医生病历和健康档案生成解读。\n"
        f"医生病历：{detail}\n"
        f"健康档案：{health}\n\n"
        "你是医疗 AI 助手。请遵循“先共情、再翻译、后行动、边界明”的顺序，"
        "将病历翻译为患者能理解的说明，而不是逐句复述或生成新的诊疗意见。\n\n"
        "输出必须是自然的纯文本段落，不得使用 Markdown 标题、列表、编号、星号、"
        "反引号或其他标记语法；每个部分必须使用空行分隔。\n\n"
        "第一段必须原样以这句话开始：“您好，以下是我根据病历信息为您整理的通俗版解读，"
        "这不能替代主治医生的面诊建议。如有任何紧急不适，请立即就医。”\n\n"
        "第二段以“病历内容概述：”开头。将病历中已明确写明的诊断、症状、体征、检查结果、"
        "治疗方案或复诊安排翻译成日常语言，解释医学术语对生活的实际含义。"
        "只解释医生已经记录的内容，不得篡改、补全或否定病历，也不得把病历提到的疾病名称"
        "解释为新的诊断。病历未提供诊断、检查、用药或复诊信息时，必须明确说明“病历未提供该信息”。\n\n"
        "第三段以“您现在需要关注的事项：”开头。仅将病历中已有的用药方法、复诊时间、"
        "观察事项和生活建议整理为清晰的行动提示；不得自行补充剂量、疗程、禁忌、相互作用、"
        "生活方式建议或治疗调整。病历没有明确医嘱时，写“请以医生当面或书面医嘱为准”。\n\n"
        "第四段以“健康档案提示：”开头。仅列出健康档案已记录的过敏史和既往史，并提示用户"
        "在后续就医或用药时主动告知医生；不得推断其与本次病历必然相关。"
        "过敏史或既往史为空时，必须明确写“健康档案未记录过敏史”或“健康档案未记录既往史”。\n\n"
        "第五段以“需要及时就医的情况：”开头。仅说明病历中医生明确交代的危险信号；"
        "若病历没有记录，写“病历未特别列出危险信号；出现突发或持续加重的不适时，请及时就医”。"
        "不得补造药物副作用、危险信号或停药要求。\n\n"
        "最后另起一段，以“如需进一步了解病历中某个术语或已有医嘱的含义，可以继续告诉我。”"
        "收尾，并保留“本说明仅供健康信息理解，不替代医生诊断和治疗建议”。"
    )


def _format_paid_order_notification(state: AgentState) -> str | None:
    """基于订单详情生成确定性的支付成功配送通知。

    Args:
        state: 含 query_drug_orders 执行结果的 Agent 状态。

    Returns:
        支付状态通知；本轮没有订单查询结果时返回 None。
    """
    for result in state.get("tool_results") or []:
        if result.get("tool_name") != "query_drug_orders":
            continue
        if not result.get("success"):
            error = result.get("error")
            message = error.get("message") if isinstance(error, dict) else None
            return f"暂时无法确认购药订单状态：{message or '订单查询失败，请稍后重试。'}"
        payload = _response_payload(result)
        if not isinstance(payload, dict):
            return "暂时无法确认购药订单状态，请稍后在购药订单中查看。"
        if payload.get("status") != "PAID":
            return "订单尚未确认支付成功，请以购药订单中的实际状态为准。"
        delivery = payload.get("delivery")
        expected_delivery_at = (
            delivery.get("expectedDeliveryAt") if isinstance(delivery, dict) else None
        )
        address = delivery.get("address") if isinstance(delivery, dict) else None
        address_message = (
            f"\n收货地址：{address}" if isinstance(address, str) and address.strip() else ""
        )
        if not isinstance(expected_delivery_at, str) or not expected_delivery_at.strip():
            return f"您已购买成功，预计送达时间待药房确认，请留意订单物流状态。{address_message}"
        try:
            expected_time = datetime.fromisoformat(expected_delivery_at.replace("Z", "+00:00"))
            formatted_time = expected_time.strftime("%Y/%m/%d %H:%M")
        except ValueError:
            formatted_time = expected_delivery_at
        return f"您已购买成功，预计{formatted_time}送达。{address_message}"
    return None


def _paid_order_reminder_action_cards(state: AgentState) -> list[dict[str, Any]] | None:
    """为未授权的已支付订单构造收货后自动提醒入口。

    Args:
        state: 含订单详情查询结果的 Agent 状态。

    Returns:
        待前端点击的受控交互卡；订单未支付、已授权或结果无效时返回 None。
    """
    drug_order_id = _positive_int(state.get("preset_drug_order_id"))
    if drug_order_id is None:
        return None
    for result in state.get("tool_results") or []:
        if result.get("tool_name") != "query_drug_orders" or not result.get("success"):
            continue
        payload = _response_payload(result)
        if not isinstance(payload, dict) or payload.get("status") != "PAID":
            return None
        # Java 权威返回已有授权状态后不重复引导用户确认同一项设置。
        activation_status = payload.get("reminderActivationStatus")
        if isinstance(activation_status, str) and activation_status.strip():
            return None
        return [
            {
                "action_type": _PRESET_AUTHORIZE_DRUG_ORDER_REMINDER_AFTER_RECEIPT,
                "title": "用药提醒",
                "summary": "收货后是否需要为您自动开启用药提醒？",
                "button_text": "开启提醒",
                "arguments": {"drug_order_id": drug_order_id},
            }
        ]
    return None


def _recommended_pharmacy_name(state: AgentState) -> str | None:
    """从待确认购药订单的受控展示信息中读取药房名称。

    Args:
        state: 当前 Agent 状态，含推荐后生成的待确认订单。

    Returns:
        推荐药房名称；展示信息缺失时返回 None。
    """
    for confirmation in state.get("pending_confirmations") or []:
        if confirmation.get("tool_name") != "create_drug_order":
            continue
        display = confirmation.get("display")
        details = display.get("details") if isinstance(display, dict) else None
        pharmacy_name = details.get("pharmacy_name") if isinstance(details, dict) else None
        if isinstance(pharmacy_name, str) and pharmacy_name.strip():
            return pharmacy_name.strip()
    return None


def _extract_degraded_health_record(
    tool_results: list[dict[str, Any]] | None,
) -> bool:
    """识别健康档案是否发生了来源降级（2026-08-07）。

    前端会话残留他账号就诊人 ID 时，``query_health_record`` 被 Java 数据隔离
    拒绝（403 / A0301）后确定性降级为查当前账号本人档案，并在成功结果 data
    中附 ``agent_degraded: True`` 来源标记（见 health.py 该函数）。本函数扫描
    本轮工具结果，命中该标记即返回 True，供 reply_node 注入降级引导。

    Args:
        tool_results: 本轮工具执行结果列表。

    Returns:
        bool: 存在降级成功的健康档案来源标记时返回 True。
    """
    for result in tool_results or []:
        if result.get("tool_name") != "query_health_record" or not result.get("success"):
            continue
        data = result.get("data")
        if not isinstance(data, dict):
            continue
        payload = data.get("data")
        if isinstance(payload, dict) and payload.get("agent_degraded"):
            return True
    return False


def _build_triage_guide(state: AgentState) -> str | None:
    """构造导诊推荐完成后的下一步引导（2026-08-07）。

    对齐原始需求"智能导诊与挂号"：导诊子图推荐科室与医生后，必须询问用户
    "是否需要预约挂号或在线问诊"，不能推荐完就结束。本函数在导诊评估
    （create_triage_assessment）已成功时返回引导提示，供 reply_node 注入
    LLM 输入；否则返回 None（不注入）。

    Args:
        state: 当前图状态，含 intent / tool_results。

    Returns:
        str | None: 导诊引导提示；非导诊意图或评估未成功时返回 None。
    """
    if state.get("intent") != "triage":
        return None
    assessed = any(
        r.get("tool_name") == "create_triage_assessment" and r.get("success")
        for r in state.get("tool_results") or []
    )
    if not assessed:
        return None
    return (
        "导诊评估与科室/医生推荐已完成。请在本轮回复末尾**明确询问用户**："
        "'需要我帮您预约挂号，还是发起在线问诊？'并根据用户下一步选择引导到"
        "对应流程（挂号查号源/创建订单，问诊填主诉/选医生）。不要推荐完就结束。"
    )


def _build_triage_followup_guide(state: AgentState) -> str | None:
    """构造导诊首轮症状追问引导（2026-08-07）。

    对齐原始需求"多轮对话理解"：导诊子图首轮（用户仅描述症状，评估尚未成功）
    时，引导 LLM 追问症状细节（部位/持续时间/体温/过敏史等），保证最少两轮
    对话再评估。本函数在 intent=triage 且 create_triage_assessment 尚未成功时
    返回提示，供 reply_node 注入；否则返回 None。

    Args:
        state: 当前图状态，含 intent / tool_results。

    Returns:
        str | None: 首轮追问引导；非导诊意图或评估已成功时返回 None。
    """
    if state.get("intent") != "triage":
        return None
    assessed = any(
        r.get("tool_name") == "create_triage_assessment" and r.get("success")
        for r in state.get("tool_results") or []
    )
    if assessed:
        return None
    return (
        "当前处于导诊首轮：用户刚描述症状，**导诊评估尚未完成**。"
        "请追问 1-2 个关键症状细节（如部位、持续时间、体温、有无伴随症状、过敏史），"
        "**不要在本轮下科室/医生推荐结论**，也不要调用导诊评估工具——待用户补充"
        "症状信息后再做评估与推荐。"
    )


def _extract_degraded_health_record(
    tool_results: list[dict[str, Any]] | None,
) -> bool:
    """识别健康档案是否发生了来源降级（2026-08-07）。

    前端会话残留他账号就诊人 ID 时，``query_health_record`` 被 Java 数据隔离
    拒绝（403 / A0301）后确定性降级为查当前账号本人档案，并在成功结果 data
    中附 ``agent_degraded: True`` 来源标记（见 health.py 该函数）。本函数扫描
    本轮工具结果，命中该标记即返回 True，供 reply_node 注入降级引导。

    Args:
        tool_results: 本轮工具执行结果列表。

    Returns:
        bool: 存在降级成功的健康档案来源标记时返回 True。
    """
    for result in tool_results or []:
        if result.get("tool_name") != "query_health_record" or not result.get("success"):
            continue
        data = result.get("data")
        if not isinstance(data, dict):
            continue
        payload = data.get("data")
        if isinstance(payload, dict) and payload.get("agent_degraded"):
            return True
    return False


def _build_triage_guide(state: AgentState) -> str | None:
    """构造导诊推荐完成后的下一步引导（2026-08-07）。

    对齐原始需求"智能导诊与挂号"：导诊子图推荐科室与医生后，必须询问用户
    "是否需要预约挂号或在线问诊"，不能推荐完就结束。本函数在导诊评估
    （create_triage_assessment）已成功时返回引导提示，供 reply_node 注入
    LLM 输入；否则返回 None（不注入）。

    Args:
        state: 当前图状态，含 intent / tool_results。

    Returns:
        str | None: 导诊引导提示；非导诊意图或评估未成功时返回 None。
    """
    if state.get("intent") != "triage":
        return None
    assessed = any(
        r.get("tool_name") == "create_triage_assessment" and r.get("success")
        for r in state.get("tool_results") or []
    )
    if not assessed:
        return None
    return (
        "导诊评估与科室/医生推荐已完成。请在本轮回复末尾**明确询问用户**："
        "'需要我帮您预约挂号，还是发起在线问诊？'并根据用户下一步选择引导到"
        "对应流程（挂号查号源/创建订单，问诊填主诉/选医生）。不要推荐完就结束。"
    )


def _build_triage_followup_guide(state: AgentState) -> str | None:
    """构造导诊首轮症状追问引导（2026-08-07）。

    对齐原始需求"多轮对话理解"：导诊子图首轮（用户仅描述症状，评估尚未成功）
    时，引导 LLM 追问症状细节（部位/持续时间/体温/过敏史等），保证最少两轮
    对话再评估。本函数在 intent=triage 且 create_triage_assessment 尚未成功时
    返回提示，供 reply_node 注入；否则返回 None。

    Args:
        state: 当前图状态，含 intent / tool_results。

    Returns:
        str | None: 首轮追问引导；非导诊意图或评估已成功时返回 None。
    """
    if state.get("intent") != "triage":
        return None
    assessed = any(
        r.get("tool_name") == "create_triage_assessment" and r.get("success")
        for r in state.get("tool_results") or []
    )
    if assessed:
        return None
    return (
        "当前处于导诊首轮：用户刚描述症状，**导诊评估尚未完成**。"
        "请追问 1-2 个关键症状细节（如部位、持续时间、体温、有无伴随症状、过敏史），"
        "**不要在本轮下科室/医生推荐结论**，也不要调用导诊评估工具——待用户补充"
        "症状信息后再做评估与推荐。"
    )


def _extract_prescription_interpretation(
    tool_results: list[dict[str, Any]] | None,
) -> dict[str, Any] | None:
    """提取本轮处方解读工具的标准化结果。

    Args:
        tool_results: 本轮工具执行结果列表。

    Returns:
        含 source 的解读载荷；本轮没有成功处方解读时返回 None。
    """
    for result in tool_results or []:
        if result.get("tool_name") != "interpret_prescription" or not result.get("success"):
            continue
        response = result.get("data")
        if not isinstance(response, dict):
            continue
        payload = response.get("data")
        if isinstance(payload, dict) and payload.get("source") in (
            _OFFICIAL_READY_SOURCE,
            _AI_FALLBACK_SOURCE,
        ):
            return payload
    return None


def _format_official_interpretation(payload: dict[str, Any]) -> str | None:
    """组合正式处方解读原文及其免责声明。

    Args:
        payload: source 为 OFFICIAL_READY 的标准化解读载荷。

    Returns:
        未改写的正式解读正文；内容缺失时返回 None。
    """
    interpretation = payload.get("interpretation")
    if not isinstance(interpretation, dict):
        return None
    content = interpretation.get("content")
    if not isinstance(content, str) or not content.strip():
        return None
    sections = [content.strip()]
    disclaimer = interpretation.get("disclaimer")
    if isinstance(disclaimer, str) and disclaimer.strip() and disclaimer.strip() not in content:
        sections.append(disclaimer.strip())
    return "\n\n".join(sections)


def _build_ai_fallback_context(payload: dict[str, Any]) -> str | None:
    """构造 AI 即时处方解读的严格回复约束。

    Args:
        payload: source 为 AI_FALLBACK 的标准化解读载荷。

    Returns:
        注入回复模型的系统上下文；处方详情缺失时返回 None。
    """
    prescription = payload.get("prescription")
    if not isinstance(prescription, dict):
        return None
    try:
        detail = json.dumps(prescription, ensure_ascii=False)
    except (TypeError, ValueError):
        return None
    return (
        "当前回复必须基于以下真实处方详情生成即时说明：\n"
        f"{detail}\n\n"
        "这是“AI 即时解读（未经过医生审核）”，首行必须使用该标题。"
        "输出必须是自然的纯文本段落，不得使用 Markdown 标题、列表、编号、星号、"
        "反引号或其他标记语法。"
        "必须使用换行分段：标题后空一行；每种药品独立成段，药品段之间空一行；"
        "药品名称，常见副作用，用药提醒等分点段落间,也要空一行；"
        "最后的总体提醒另起一段并与药品段空一行。"
        "每种药品段内按“药品名称：”“主要用途：”“常见副作用：”“用药提醒：”分行输出，"
        "每个字段各占一行，字段之间不使用项目符号。"
        "对处方中的每种药品，使用通俗语言说明其常见用途、可能帮助缓解的常见症状、"
        "常见副作用和关键用药注意事项；用途和副作用使用“通常”“可能”等表述，"
        "不能据此判断用户患有对应疾病。"
        "可结合药品通用知识提示孕哺期、严重肝肾功能异常、过敏史、驾车和检查前停药等注意事项，"
        "但不编造具体停药时长、禁忌或相互作用；不确定时应建议咨询医生或药师。"
        "处方中的规格、单次用量、频次、用法和疗程必须按原始数据复述；任一字段缺失时必须明确写“处方未提供该信息”。"
        "不得调整剂量、频次、疗程，也不得要求用户自行停药或换药。"
        "末尾必须写“本说明仅供健康信息理解，请遵医嘱用药；如有不适请及时联系医生。”"
    )


async def reply_node(state: AgentState) -> dict[str, Any]:
    """回复生成节点（系分 §5.12）。

    汇总工具结果或 LLM 输出，调用 LLM 生成自然语言回复。
    医疗免责声明由前端卡片统一承担，此处不再注入。

    Args:
        state: 当前图状态，包含 messages / tool_results 等字段。

    Returns:
        dict: 包含新增的 assistant 消息（由 LangGraph add_messages reducer 累积）。

    Raises:
        无：生成失败时返回降级话术。
    """
    try:
        preset_error = state.get("preset_error")
        if isinstance(preset_error, str) and preset_error.strip():
            # 受控预设的校验和推荐失败由服务端直接说明，避免模型自行推断原因。
            return {"messages": [{"role": "assistant", "content": preset_error.strip()}]}

        record_picker = _build_interpretation_record_picker(state)
        if record_picker is not None:
            return record_picker

        if state.get("preset_action") == _PRESET_NOTIFY_DRUG_ORDER_PAID:
            notification = _format_paid_order_notification(state)
            if notification:
                # 预计送达时间必须来自 Java 订单详情，不能交给模型生成。
                result: dict[str, Any] = {
                    "messages": [{"role": "assistant", "content": notification}]
                }
                action_cards = _paid_order_reminder_action_cards(state)
                if action_cards:
                    result["action_cards"] = action_cards
                return result

        if state.get("preset_action") == _PRESET_RECOMMEND_PRESCRIPTION_PHARMACY:
            pending_confirmations = state.get("pending_confirmations") or []
            if pending_confirmations:
                # 推荐第一项已确定，用户只需对创建待支付订单做既有 L2 确认。
                pharmacy_name = _recommended_pharmacy_name(state) or "推荐药店"
                return {
                    "messages": [
                        {
                            "role": "assistant",
                            "content": (
                                f"已为您找到综合推荐的有货药店“{pharmacy_name}”，请确认是否购买。"
                            ),
                        }
                    ]
                }

        interpretation = _extract_prescription_interpretation(state.get("tool_results"))
        if interpretation and interpretation.get("source") == _OFFICIAL_READY_SOURCE:
            official_content = _format_official_interpretation(interpretation)
            if official_content:
                # 正式解读由医生或既有生产链路确认，必须原样展示，不能交给 LLM 改写。
                action_cards, address_message = _interpretation_action_cards(state)
                content = official_content
                if address_message:
                    content = f"{content}\n\n{address_message}"
                result: dict[str, Any] = {"messages": [{"role": "assistant", "content": content}]}
                if action_cards:
                    result["action_cards"] = action_cards
                return result

        llm = build_llm()

        intent = state.get("intent", "qa")
        scope = state.get("scope", "c_end")
        system_prompt = REPLY_SYSTEM_PROMPT.format(
            intent=intent, scope=scope, today=date.today().isoformat()
        )

        # 构造 LLM 输入（不修改 state.messages，避免副作用）
        llm_messages: list[dict[str, Any]] = [{"role": "system", "content": system_prompt}]
        history = truncate_messages(state.get("messages", []), get_settings().memory_window_size)
        llm_messages.extend(history)

        # 如果有 RAG 检索知识，注入到上下文（本轮有效，不写入历史）
        rag_context = state.get("rag_context")
        if rag_context:
            llm_messages.append({"role": "system", "content": rag_context})

        # 如果有工具调用结果，注入到上下文
        tool_results = state.get("tool_results")
        if tool_results:
            tool_summary = _format_tool_results(tool_results)
            llm_messages.append({"role": "system", "content": f"工具调用结果：\n{tool_summary}"})

        # 档案来源降级提示（2026-08-07）：前端会话残留了他账号的就诊人 ID 时，
        # query_health_record 被 Java 数据隔离拒绝后降级为查当前账号本人档案
        # （见 health.py query_health_record）。此处识别该来源标记，引导 LLM
        # 如实说明档案来源、提示用户重新选择就诊人，且禁忌核对不得把本人档案
        # 当成目标就诊人的档案，避免基于错误的过敏史做医疗判断。
        degraded = _extract_degraded_health_record(tool_results)
        if degraded:
            llm_messages.append(
                {
                    "role": "system",
                    "content": (
                        "当前读取的健康档案为**当前登录账号本人**的档案（前端选中的就诊人"
                        "无权访问，系统已自动降级读取本人档案）。"
                        "请如实告知用户：健康档案未能按所选就诊人读取，已改用本人档案；"
                        "如需按指定就诊人查看，请返回首页重新选择就诊人。"
                        "⚠️ 禁忌核对等医疗判断必须基于本人档案，不得声称已核对目标就诊人的过敏史。"
                    ),
                }
            )

        # 导诊推荐完成引导（2026-08-07）：导诊子图（intent=triage）推荐科室与医生
        # 后，必须询问用户是否需要预约挂号或在线问诊（原始需求"智能导诊与挂号"）。
        # 软约束（TRIAGE_SCENE_PROMPT）不可靠，此处确定性注入引导：LLM 已成功调
        # 用 create_triage_assessment 完成评估后，回复末尾询问下一步。
        triage_guide = _build_triage_guide(state)
        if triage_guide:
            llm_messages.append({"role": "system", "content": triage_guide})

        # 导诊首轮追问引导（2026-08-07）：导诊子图首轮（评估被 tool_caller 拦截、
        # 尚未成功评估）时，注入"继续追问症状细节"引导，保证最少两轮对话——
        # 用户仅描述症状后不直接下结论，先追问部位/持续时间/体温/过敏史等。
        triage_followup = _build_triage_followup_guide(state)
        if triage_followup:
            llm_messages.append({"role": "system", "content": triage_followup})

        if interpretation and interpretation.get("source") == _AI_FALLBACK_SOURCE:
            fallback_context = _build_ai_fallback_context(interpretation)
            if fallback_context:
                # 即时说明只能使用已授权读取的处方详情，避免模型补造医疗事实。
                llm_messages.append({"role": "system", "content": fallback_context})

        medical_record_interpretation = _extract_medical_record_interpretation(tool_results)
        if medical_record_interpretation:
            medical_record_context = _build_medical_record_interpretation_context(
                medical_record_interpretation
            )
            if medical_record_context:
                # 病历解读只允许模型解释已授权读取的原文和健康档案，不能生成诊断或修改建议。
                llm_messages.append({"role": "system", "content": medical_record_context})

        # 如果有待确认的 L2 操作，提醒 LLM 在回复中引导用户查看确认卡片
        pending_confirmations = state.get("pending_confirmations")
        if pending_confirmations:
            tool_names = [p.get("tool_name", "") for p in pending_confirmations]
            prompt = (
                f"有 {len(pending_confirmations)} 个操作（{', '.join(tool_names)}）正在等待用户"
                "点击确认卡片，**操作尚未执行**。请用简短回复说明用户即将执行的操作，"
                "并引导用户查看并点击确认卡片。"
                "⚠️ 严禁声称操作已成功/已提交/已发送/已收到——确认卡片点击后才会真正执行，"
                "确认前一律用'即将/待确认'表述，不要替用户完成操作。"
            )
            llm_messages.append({"role": "system", "content": prompt})

        # M8-7：选医生卡片已就绪时，引导 LLM 回复"请在卡片中选择"，
        # 禁止提及号源余量/预约时间/线下就医（在线问诊不依赖当天号源，
        # 避免 LLM 把 query_doctors 的 availableCount=0 误读为"号源已满"）。
        pending_doctor_choices = state.get("pending_doctor_choices")
        if pending_doctor_choices:
            names = "、".join(
                str(c.get("name") or c.get("doctor_id") or "医生") for c in pending_doctor_choices
            )
            llm_messages.append(
                {
                    "role": "system",
                    "content": (
                        f"医生列表（{names}）已生成选择卡片，用户可直接点选。"
                        "请用简短回复告知用户'已为您找到以下医生，请在卡片中选择'，"
                        "然后停止。⚠️ 不得提及号源余量、号源已满、预约时间、线下就医"
                        "或挂号——在线问诊随时可发起，不依赖医生当天号源。"
                    ),
                }
            )

        # 风险标记注入（系分 §7.1）：safety_check 记录的未完成/被拒操作，
        # 要求 LLM 如实告知用户，避免静默失败
        risk_warning = _format_risk_flags(state.get("risk_flags", []))
        if risk_warning:
            content = f"重要提示（必须在回复中如实告知用户）：{risk_warning}。语气平和，不夸大。"
            llm_messages.append({"role": "system", "content": content})

        response = await llm.ainvoke(llm_messages)
        # BaseMessage.content 可为 str 或多段内容列表（OpenAI 格式），统一转 str
        raw_content = response.content
        reply_content = raw_content if isinstance(raw_content, str) else str(raw_content)
        # 确定性去除 Markdown 强调标记（提示词约束的兜底，见 _strip_markdown_emphasis）
        reply_content = _strip_markdown_emphasis(reply_content)

        action_cards: list[dict[str, Any]] | None = None
        if interpretation:
            action_cards, address_message = _interpretation_action_cards(state)
            if address_message:
                reply_content = f"{reply_content.rstrip()}\n\n{address_message}"

        logger.info("回复生成成功: 长度=%d, 意图=%s", len(reply_content), intent)
        result = {"messages": [{"role": "assistant", "content": reply_content}]}
        if action_cards:
            result["action_cards"] = action_cards
        return result

    except Exception as e:
        logger.error("回复生成失败: %s", e)
        fallback_message = "抱歉，我遇到了一些问题，请稍后重试。"
        return {"messages": [{"role": "assistant", "content": fallback_message}]}


def _format_risk_flags(risk_flags: list[str]) -> str | None:
    """风险标记转中文警告（系分 §7.1：safety_check 追加，reply 注入回复）。

    支持 safety_check 写入的两种前缀：
    - ``redis_unavailable_{tool}``：L2 工具因 Redis 不可用被拒绝执行
    - ``blocked_{tool}``：L3/L4 未授权工具被拦截

    Args:
        risk_flags: AgentState.risk_flags（safety_check 追加的风险标记列表）。

    Returns:
        str | None: 中文警告文本；无风险标记时返回 None。
    """
    warnings: list[str] = []
    for flag in risk_flags:
        if flag.startswith("redis_unavailable_"):
            tool = flag.removeprefix("redis_unavailable_")
            warnings.append(f"「{tool}」操作因系统暂时不可用未能执行")
        elif flag.startswith("blocked_"):
            tool = flag.removeprefix("blocked_")
            warnings.append(f"「{tool}」操作未获授权，已拦截")
    if not warnings:
        return None
    return "；".join(warnings)


def _format_tool_results(tool_results: list[dict[str, Any]]) -> str:
    """格式化工具调用结果为 LLM 可读的自然语言文本。

    从 Java 响应信封（{code, message, data, traceId}）提取内层业务数据，
    JSON 序列化后注入 LLM 上下文，确保 LLM 基于真实数据生成回复。

    Args:
        tool_results: 工具执行结果列表。

    Returns:
        str: 含真实业务数据的文本摘要。
    """
    summaries = []
    # 截断阈值入 settings（P3-5），避免硬编码；循环外取一次避免重复读配置
    max_chars = get_settings().max_data_chars
    for result in tool_results:
        tool_name = result.get("tool_name", "未知工具")
        success = result.get("success", False)

        if success:
            data = result.get("data", {})
            # 尝试解 Java 响应信封（{code, message, data, traceId}），提取内层业务数据
            if isinstance(data, dict) and "data" in data and data.get("data") is not None:
                payload = data["data"]
            else:
                payload = data  # 本地工具或非信封格式
            try:
                text = json.dumps(payload, ensure_ascii=False)
            except (TypeError, ValueError):
                text = str(payload)
            if len(text) > max_chars:
                text = text[:max_chars] + "...(截断)"
            summaries.append(f"✓ {tool_name}: {text}")
        else:
            error_msg = result.get("error", {}).get("message", "未知错误")
            summaries.append(f"✗ {tool_name}: 执行失败 - {error_msg}")

    return "\n".join(summaries)


def _strip_markdown_emphasis(text: str) -> str:
    """去除回复文本中的 Markdown 强调标记（2026-08-07）。

    LLM 输出可能残留 ``**加粗**``、``*斜体*``、``- 列表`` 等 Markdown 标记，
    前端纯文本渲染会把星号/井号原样显示。本函数确定性清除这些标记，作为
    提示词约束（REPLY_SYSTEM_PROMPT）的兜底——不依赖模型遵循。

    处理规则（不破坏合法文本）：
        - ``**...**`` / ``__...__``：成对加粗，保留内部文本
        - ``*...*`` / ``_..._``：成对斜体，保留内部文本；**星号须紧贴非数字
          文本**——数字间的 ``*``（如剂量 "2*2"、体温 "37*5"）是乘号，不删
        - 行首 ``-`` / ``*`` 列表项标记：删除（保留后续内容）
        - 行首 ``#`` / ``##`` 标题标记：删除（保留后续内容）
        - 反引号对 ``...``：成对，保留内部文本
        - 孤立的 ``*`` / ``#`` / ``_``（不成对、不在行首、紧贴数字）保留，
          避免误删正常文本

    Args:
        text: LLM 生成的回复原文。

    Returns:
        去除 Markdown 强调标记后的纯文本。
    """
    import re

    # 成对加粗 / 下划线加粗 / 反引号：内部任意，保留内部文本
    for pattern in (r"\*\*(.+?)\*\*", r"__(.+?)__", r"`(.+?)`"):
        text = re.sub(pattern, r"\1", text)
    # 成对斜体：星号紧贴非数字文本（数字间乘号不删）。
    #   *注意* / *孙医生*     -> 删除星号（主正则，星号后紧贴非数字）
    #   2*2 / 37*5            -> 保留（星号两侧是数字，是乘号非斜体）
    #   *3次* / *5片*         -> 删除星号（兜底正则，数字后紧跟汉字）
    # 规则：内部必须含至少一个非数字字符才按斜体处理；纯数字（剂量/乘号）保留。
    for pattern in (r"\*(?!\d)(.+?)(?<!\d)\*", r"_(?!\d)(.+?)(?<!\d)_"):
        text = re.sub(pattern, r"\1", text)
    # 兜底：数字开头的斜体（*3次*、*5片*——数字后紧跟汉字，中文语境为强调
    # 标记）。星号后须为「数字+汉字」开头，杜绝误删乘号（"37*5（剂量 2*2"：
    # "5" 后是括号非汉字，不匹配）。
    text = re.sub(r"\*(\d[一-鿿][^*\n]*?)\*", r"\1", text)
    # 行首列表/标题标记：- / * / #+ / > 后跟空白
    text = re.sub(r"(?m)^[ \t]*(?:[-*>]|#+)[ \t]+", "", text)
    return text
