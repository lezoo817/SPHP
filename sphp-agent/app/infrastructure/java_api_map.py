"""Java 后端接口契约表（系分 §6.4 / §5.3）。

集中维护 Agent 全部工具对应的 Java REST 接口定义，作为 Java 接口
变化的**单一改动点**：Java 侧 URL 路径 / HTTP 方法 / scope 前缀变更时，
只需改本表，无需触碰各工具文件（mcp_server/tools/*.py）。

key 为语义接口名：优先与工具同名，双路径/多接口工具用「工具名:变体」后缀
（如 ``query_appointments:list`` / ``query_appointments:detail``）。

每个条目:
    method: HTTP 方法（GET/POST/PUT/PATCH/DELETE）
    path: 相对路径，``{param}`` 为路径参数占位（调用时替换）
    scope: c_end / b_end，决定 Java base_url 前缀

注意：B 端接口基于系分 V1.0，Java 端完成后可能需要校正本表。
"""

# 接口名 -> 接口定义
JAVA_API_MAP: dict[str, dict[str, str]] = {
    # ---- C 端：导诊 ----
    "create_triage_assessment": {
        "method": "POST",
        "path": "/api/c/v1/triage/assessments",
        "scope": "c_end",
    },
    # ---- C 端：挂号查询 ----
    "query_departments": {
        "method": "GET",
        "path": "/api/c/v1/departments",
        "scope": "c_end",
    },
    "query_doctors": {
        "method": "GET",
        "path": "/api/c/v1/doctors",
        "scope": "c_end",
    },
    "query_schedule_slots": {
        "method": "GET",
        "path": "/api/c/v1/doctors/{doctor_id}/slots",
        "scope": "c_end",
    },
    # ---- C 端：挂号订单 ----
    "create_appointment": {
        "method": "POST",
        "path": "/api/c/v1/appointments",
        "scope": "c_end",
    },
    "query_appointments:list": {
        "method": "GET",
        "path": "/api/c/v1/appointments",
        "scope": "c_end",
    },
    "query_appointments:detail": {
        "method": "GET",
        "path": "/api/c/v1/appointments/{appointment_id}",
        "scope": "c_end",
    },
    "cancel_appointment": {
        "method": "POST",
        "path": "/api/c/v1/appointments/{appointment_id}/cancel",
        "scope": "c_end",
    },
    "join_waitlist": {
        "method": "POST",
        "path": "/api/c/v1/waitlists",
        "scope": "c_end",
    },
    "query_payment_status": {
        "method": "GET",
        "path": "/api/c/v1/payments/{payment_id}",
        "scope": "c_end",
    },
    # ---- C 端：问诊 ----
    "save_pre_consultation": {
        "method": "POST",
        "path": "/api/c/v1/consultations/pre-consultations",
        "scope": "c_end",
    },
    "query_consultations:list": {
        "method": "GET",
        "path": "/api/c/v1/consultations",
        "scope": "c_end",
    },
    "query_consultations:detail": {
        "method": "GET",
        "path": "/api/c/v1/consultations/{consultation_id}",
        "scope": "c_end",
    },
    "send_consultation_message": {
        "method": "POST",
        "path": "/api/c/v1/consultations/{consultation_id}/messages",
        "scope": "c_end",
    },
    # ---- C 端：处方 ----
    "query_prescriptions:list": {
        "method": "GET",
        "path": "/api/c/v1/prescriptions",
        "scope": "c_end",
    },
    "query_prescriptions:detail": {
        "method": "GET",
        "path": "/api/c/v1/prescriptions/{prescription_id}",
        "scope": "c_end",
    },
    "interpret_prescription": {
        "method": "GET",
        "path": "/api/c/v1/prescriptions/{prescription_id}/interpretation",
        "scope": "c_end",
    },
    # ---- C 端：健康档案 ----
    "query_health_record": {
        "method": "GET",
        "path": "/api/c/v1/health-record",
        "scope": "c_end",
    },
    "manage_allergy:create": {
        "method": "POST",
        "path": "/api/c/v1/health-record/allergies",
        "scope": "c_end",
    },
    "manage_allergy:update": {
        "method": "PUT",
        "path": "/api/c/v1/health-record/allergies/{allergy_id}",
        "scope": "c_end",
    },
    "manage_medical_history:create": {
        "method": "POST",
        "path": "/api/c/v1/health-record/histories",
        "scope": "c_end",
    },
    "manage_medical_history:update": {
        "method": "PUT",
        "path": "/api/c/v1/health-record/histories/{history_id}",
        "scope": "c_end",
    },
    # ---- C 端：检查报告 ----
    "query_reports:list": {
        "method": "GET",
        "path": "/api/c/v1/reports",
        "scope": "c_end",
    },
    "query_reports:detail": {
        "method": "GET",
        "path": "/api/c/v1/reports/{report_id}",
        "scope": "c_end",
    },
    "query_reports:interpretation": {
        "method": "GET",
        "path": "/api/c/v1/reports/{report_id}/interpretation",
        "scope": "c_end",
    },
    "create_report": {
        "method": "POST",
        "path": "/api/c/v1/reports",
        "scope": "c_end",
    },
    # ---- C 端：用药计划 ----
    "query_medication_plans": {
        "method": "GET",
        "path": "/api/c/v1/medication-plans",
        "scope": "c_end",
    },
    "update_medication_plan": {
        "method": "PATCH",
        "path": "/api/c/v1/medication-plans/{plan_id}",
        "scope": "c_end",
    },
    # ---- C 端：随访 ----
    "query_follow_ups": {
        "method": "GET",
        "path": "/api/c/v1/follow-ups",
        "scope": "c_end",
    },
    "confirm_follow_up": {
        "method": "POST",
        "path": "/api/c/v1/follow-ups/{follow_up_id}/confirm",
        "scope": "c_end",
    },
    # ---- C 端：通知 ----
    "manage_notifications:list": {
        "method": "GET",
        "path": "/api/c/v1/notifications",
        "scope": "c_end",
    },
    "manage_notifications:read": {
        "method": "POST",
        "path": "/api/c/v1/notifications/{notification_id}/read",
        "scope": "c_end",
    },
    # ---- C 端：购药 ----
    "query_pharmacy_stock": {
        "method": "GET",
        "path": "/api/c/v1/pharmacies/inventory",
        "scope": "c_end",
    },
    "create_drug_order": {
        "method": "POST",
        "path": "/api/c/v1/drug-orders",
        "scope": "c_end",
    },
    "query_drug_orders:list": {
        "method": "GET",
        "path": "/api/c/v1/drug-orders",
        "scope": "c_end",
    },
    "query_drug_orders:detail": {
        "method": "GET",
        "path": "/api/c/v1/drug-orders/{drug_order_id}",
        "scope": "c_end",
    },
    "cancel_drug_order": {
        "method": "POST",
        "path": "/api/c/v1/drug-orders/{drug_order_id}/cancel",
        "scope": "c_end",
    },
    "confirm_drug_receipt": {
        "method": "POST",
        "path": "/api/c/v1/drug-orders/{drug_order_id}/confirm-receipt",
        "scope": "c_end",
    },
    # ---- B 端：患者聚合 ----
    "query_patient_history:base": {
        "method": "GET",
        "path": "/api/b/patients/{patient_id}",
        "scope": "b_end",
    },
    "query_patient_history:visits": {
        "method": "GET",
        "path": "/api/b/patients/{patient_id}/visits",
        "scope": "b_end",
    },
    "query_patient_history:prescriptions": {
        "method": "GET",
        "path": "/api/b/patients/{patient_id}/prescriptions",
        "scope": "b_end",
    },
    "query_patient_history:medications": {
        "method": "GET",
        "path": "/api/b/patients/{patient_id}/medications",
        "scope": "b_end",
    },
    "query_patient_medications": {
        "method": "GET",
        "path": "/api/b/patients/{patient_id}/medications",
        "scope": "b_end",
    },
    # ---- B 端：药品与病历 ----
    "query_drug_guide": {
        "method": "GET",
        "path": "/api/b/drugs",
        "scope": "b_end",
    },
    "generate_draft_note": {
        "method": "PUT",
        "path": "/api/b/doctor/consult/{consultation_id}/note",
        "scope": "b_end",
    },
    # ---- B 端：医生推荐（聚合）----
    "recommend_care:departments": {
        "method": "GET",
        "path": "/api/b/admin/departments",
        "scope": "b_end",
    },
    "recommend_care:doctors": {
        "method": "GET",
        "path": "/api/b/admin/doctors",
        "scope": "b_end",
    },
    "recommend_care:schedules": {
        "method": "GET",
        "path": "/api/b/schedules",
        "scope": "b_end",
    },
}


def resolve_api(api_name: str, path_params: dict | None = None) -> tuple[str, str, str]:
    """根据接口名查契约表，返回 (method, path, scope)。

    路径中的 ``{param}`` 占位符用 ``path_params`` 替换；缺失参数抛 KeyError。

    Args:
        api_name: 语义接口名（JAVA_API_MAP 的 key）。
        path_params: 路径参数 {参数名: 值}，替换 path 中的 {param}。

    Returns:
        (method, path, scope)。

    Raises:
        KeyError: 接口名不存在，或路径参数缺失。
    """
    if api_name not in JAVA_API_MAP:
        raise KeyError(f"未知的 Java 接口: {api_name}")
    method = JAVA_API_MAP[api_name]["method"]
    path = JAVA_API_MAP[api_name]["path"]
    scope = JAVA_API_MAP[api_name]["scope"]

    if path_params:
        for k, v in path_params.items():
            placeholder = "{" + k + "}"
            if placeholder in path:
                path = path.replace(placeholder, str(v))
            else:
                raise KeyError(f"接口 {api_name} 的路径中不存在占位符 {placeholder}")

    return method, path, scope
