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
from typing import Any, cast


async def _wrap(
    module: Any, func_name: str, arguments: dict[str, Any], user_id: int | None
) -> dict[str, Any]:
    """按参数名绑定调用 MCP 工具封装函数（忽略未知参数，注入 user_id）。"""
    func = getattr(module, func_name)
    sig = inspect.signature(func)
    kwargs = {k: v for k, v in arguments.items() if k in sig.parameters}
    if "user_id" in sig.parameters:
        kwargs["user_id"] = user_id
    return cast(dict[str, Any], await func(**kwargs))


# 工具名 -> 模块 import 路径（对应 mcp_server/tools/*.py）
_MCP_TOOL_MODULES = {
    "triage": "app.mcp_server.tools.triage",
    "appointment": "app.mcp_server.tools.appointment",
    "consultation": "app.mcp_server.tools.consultation",
    "health": "app.mcp_server.tools.health",
    "notification": "app.mcp_server.tools.notification",
    "pharmacy": "app.mcp_server.tools.pharmacy",
    "prescription": "app.mcp_server.tools.prescription",
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
    # 购药
    "query_pharmacy_stock": ("pharmacy", "query_pharmacy_stock"),
    "create_drug_order": ("pharmacy", "create_drug_order"),
    "query_drug_orders": ("pharmacy", "query_drug_orders"),
    "cancel_drug_order": ("pharmacy", "cancel_drug_order"),
    "confirm_drug_receipt": ("pharmacy", "confirm_drug_receipt"),
    # 健康管理
    "query_health_record": ("health", "query_health_record"),
    "manage_allergy": ("health", "manage_allergy"),
    "manage_medical_history": ("health", "manage_medical_history"),
    "query_reports": ("health", "query_reports"),
    "create_report": ("health", "create_report"),
    "query_medication_plans": ("health", "query_medication_plans"),
    "update_medication_plan": ("health", "update_medication_plan"),
    "query_follow_ups": ("health", "query_follow_ups"),
    "confirm_follow_up": ("health", "confirm_follow_up"),
    "manage_notifications": ("notification", "manage_notifications"),
    # B 端
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
