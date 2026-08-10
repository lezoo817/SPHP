"""MCP 工具分发器（M6-C1 从编排层 tool_executor 迁入）。

统一持有工具名 -> 封装函数的映射与调用逻辑，供三处复用：
    - MCP Server ``tools/call`` 处理器（app/mcp_server/server.py）
    - tool_executor 的 MCP Client 回退直调路径（app/orchestrator/nodes/tool_executor.py）
    - 独立调用（确认回执、联调脚本）

关键设计：
    - 纯数据搬运：封装函数内部直连 call_java_api 调 Java REST，不承载推理
    - user_id 不在 ToolSchema 参数里，由调用方显式注入
    - 未知工具由 is_registered() 先判，dispatch_tool 内部 KeyError 兜底
"""

import importlib
import inspect
import logging
from typing import Any, cast

from app.infrastructure.java_client import (
    reset_idempotency_context,
    reset_jwt_context,
    set_idempotency_context,
    set_jwt_context,
)

logger = logging.getLogger(__name__)

# 确认流程内部键（P2 #17）：chat_confirm 将 confirm_token 记录的幂等键注入
# 该键，经 MCP 协议透传至此。不在任何 ToolSchema 参数中，LLM 不会伪造；
# _wrap 剥离后注入 ContextVar，供同任务栈内封装函数的 call_java_api 复用，
# 确保同一确认操作（含失败重试）Java 侧幂等去重。
_IDEMPOTENCY_ARG = "__idempotency_key__"

# JWT 透传内部键（与幂等键对称，C 端拦截器硬需求）：tool_executor 将
# AgentState.jwt_token 注入此键，经 MCP 协议透传至此。不在任何 ToolSchema
# 参数中，LLM 不会伪造；_wrap 剥离后注入 ContextVar，供封装函数内
# call_java_api 读 Authorization: Bearer 头，C 端拦截器校验通过。
_JWT_ARG = "__jwt_token__"


async def _wrap(
    module: Any, func_name: str, arguments: dict[str, Any], user_id: int | None
) -> dict[str, Any]:
    """按参数名绑定调用 MCP 工具封装函数（忽略未知参数，注入 user_id）。

    P2 #17：剥离确认流程注入的 ``__idempotency_key__``（不传给业务参数），
    若有则设置幂等键 ContextVar，使封装函数内 call_java_api 的写操作复用
    该键；await 结束后恢复，不影响同任务后续调用。

    JWT 透传：对称剥离 ``__jwt_token__``，设置 JWT ContextVar，使封装函数内
    call_java_api 注入 Authorization: Bearer 头（C 端拦截器强制、拒绝
    X-User-Id）；await 结束后恢复。
    """
    func = getattr(module, func_name)
    sig = inspect.signature(func)
    # P3-6：未知参数不静默丢弃——业务参数演进时暴露给日志排查（debug 级，
    # 不改变行为：不在签名中的参数继续忽略，保持向后兼容；内部幂等键除外）。
    known = {k for k in arguments if k in sig.parameters}
    unknown = {k for k in arguments if k not in known and k not in (_IDEMPOTENCY_ARG, _JWT_ARG)}
    if unknown:
        logger.debug("工具 %s 忽略未知参数: %s", func_name, sorted(unknown))
    kwargs = {k: arguments[k] for k in known}
    if "user_id" in sig.parameters:
        kwargs["user_id"] = user_id

    # 幂等键 / JWT 均经 ContextVar 透传至封装函数内 call_java_api；
    # finally 反序恢复（LIFO），互不影响。
    idem_key = arguments.get(_IDEMPOTENCY_ARG)
    jwt = arguments.get(_JWT_ARG)
    idem_handle = set_idempotency_context(idem_key) if idem_key else None
    jwt_handle = set_jwt_context(jwt) if jwt else None
    try:
        return cast(dict[str, Any], await func(**kwargs))
    finally:
        if jwt_handle is not None:
            reset_jwt_context(jwt_handle)
        if idem_handle is not None:
            reset_idempotency_context(idem_handle)


# 工具名 -> 模块 import 路径（对应 mcp_server/tools/*.py）
_MCP_TOOL_MODULES = {
    "triage": "app.mcp_server.tools.triage",
    "appointment": "app.mcp_server.tools.appointment",
    "consultation": "app.mcp_server.tools.consultation",
    "health": "app.mcp_server.tools.health",
    "notification": "app.mcp_server.tools.notification",
    "pharmacy": "app.mcp_server.tools.pharmacy",
    "prescription": "app.mcp_server.tools.prescription",
    "medical_record": "app.mcp_server.tools.medical_record",
    "b_doctor": "app.mcp_server.tools.b_doctor",
}

# 工具名 -> (模块 key, 函数名)，签名与 ToolSchema 参数一致
_MCP_TOOL_FUNCS: dict[str, tuple[str, str]] = {
    # 导诊
    "create_triage_assessment": ("triage", "create_triage_assessment"),
    # 挂号查询
    "query_departments": ("appointment", "query_departments"),
    "query_doctors": ("appointment", "query_doctors"),
    "query_schedule_slots": ("appointment", "query_schedule_slots"),
    # 挂号订单
    "create_appointment": ("appointment", "create_appointment"),
    "query_appointments": ("appointment", "query_appointments"),
    "cancel_appointment": ("appointment", "cancel_appointment"),
    "join_waitlist": ("appointment", "join_waitlist"),
    "query_payment_status": ("appointment", "query_payment_status"),
    # 问诊
    "save_pre_consultation": ("consultation", "save_pre_consultation"),
    "query_consultations": ("consultation", "query_consultations"),
    "send_consultation_message": ("consultation", "send_consultation_message"),
    # 处方
    "query_prescriptions": ("prescription", "query_prescriptions"),
    "interpret_prescription": ("prescription", "interpret_prescription"),
    "interpret_medical_record": ("medical_record", "interpret_medical_record"),
    "query_medical_records": ("medical_record", "query_medical_records"),
    # 购药
    "query_pharmacy_stock": ("pharmacy", "query_pharmacy_stock"),
    "recommend_pharmacies": ("pharmacy", "recommend_pharmacies"),
    "create_drug_order": ("pharmacy", "create_drug_order"),
    "authorize_drug_order_reminder_after_receipt": (
        "pharmacy",
        "authorize_drug_order_reminder_after_receipt",
    ),
    "query_drug_orders": ("pharmacy", "query_drug_orders"),
    "cancel_drug_order": ("pharmacy", "cancel_drug_order"),
    "confirm_drug_receipt": ("pharmacy", "confirm_drug_receipt"),
    # 健康管理
    "query_health_record": ("health", "query_health_record"),
    "manage_allergy": ("health", "manage_allergy"),
    "manage_medical_history": ("health", "manage_medical_history"),
    "delete_allergy": ("health", "delete_allergy"),
    "delete_medical_history": ("health", "delete_medical_history"),
    "query_reports": ("health", "query_reports"),
    "create_report": ("health", "create_report"),
    "query_medication_plans": ("health", "query_medication_plans"),
    "update_medication_plan": ("health", "update_medication_plan"),
    "query_follow_ups": ("health", "query_follow_ups"),
    "confirm_follow_up": ("health", "confirm_follow_up"),
    "manage_notifications": ("notification", "manage_notifications"),
    # B 端
    "search_patient": ("b_doctor", "search_patient"),
    "query_patient_history": ("b_doctor", "query_patient_history"),
    "query_drug_guide": ("b_doctor", "query_drug_guide"),
    "check_drug_interaction": ("b_doctor", "check_drug_interaction"),
    "generate_draft_note": ("b_doctor", "generate_draft_note"),
    "recommend_care": ("b_doctor", "recommend_care"),
    "check_contraindication": ("b_doctor", "check_contraindication"),
    "check_allergy_risk": ("b_doctor", "check_allergy_risk"),
    "check_duplicate_medication": ("b_doctor", "check_duplicate_medication"),
}


def is_registered(tool_name: str) -> bool:
    """工具是否在 MCP 分发映射中（供执行前快速校验）。"""
    return tool_name in _MCP_TOOL_FUNCS


async def dispatch_tool(
    tool_name: str, arguments: dict[str, Any], user_id: int | None = None
) -> dict[str, Any]:
    """直调 MCP 工具封装函数（返回封装函数的结果 dict）。

    Args:
        tool_name: 工具名，须在 _MCP_TOOL_FUNCS 中。
        arguments: 工具参数（不含 user_id）。
        user_id: 注入封装函数的用户身份（Java X-User-Id）。

    Returns:
        dict: 封装函数返回的 Java 信封或聚合结构 dict。

    Raises:
        KeyError: 工具未注册时抛出。
    """
    module_key, func_name = _MCP_TOOL_FUNCS[tool_name]
    module = importlib.import_module(_MCP_TOOL_MODULES[module_key])
    return await _wrap(module, func_name, arguments, user_id)
