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

注意：B 端接口路径已对齐系分 V1.1——patient/drug/department/doctor/schedule
类 API 统一在 ``/api/b/admin/*`` 下，doctor 专属操作在 ``/api/b/doctor/*`` 下。
"""

from typing import Any

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
    # ---- B 端：患者聚合（/api/b/admin/*，系分 V1.1）----
    "query_patient_history:base": {
        "method": "GET",
        "path": "/api/b/admin/patients/{patient_id}",
        "scope": "b_end",
    },
    "query_patient_history:visits": {
        "method": "GET",
        "path": "/api/b/admin/patients/{patient_id}/visits",
        "scope": "b_end",
    },
    "query_patient_history:prescriptions": {
        "method": "GET",
        "path": "/api/b/admin/patients/{patient_id}/prescriptions",
        "scope": "b_end",
    },
    # 注：query_patient_medications 已并入 query_patient_history:medications（同路径），
    # 避免契约表冗余；独立工具 check_duplicate_medication 等复用该子接口名。
    "query_patient_history:medications": {
        "method": "GET",
        "path": "/api/b/admin/patients/{patient_id}/medications",
        "scope": "b_end",
    },
    # ---- B 端：药品与病历 ----
    "query_drug_guide": {
        "method": "GET",
        "path": "/api/b/admin/drugs",
        "scope": "b_end",
    },
    "generate_draft_note": {
        "method": "PUT",
        "path": "/api/b/doctor/consult/{consultation_id}/note",
        "scope": "b_end",
    },
    # ---- B 端：医生推荐（聚合，/api/b/admin/*）----
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
        "path": "/api/b/admin/schedules",
        "scope": "b_end",
    },
}


def resolve_api(api_name: str, path_params: dict[str, Any] | None = None) -> tuple[str, str, str]:
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


def validate_contract() -> None:
    """启动期校验接口契约表（系分 §6.4，fail-fast）。

    在 lifespan 启动时调用，发现契约错误立即报错而非运行期静默降级：
    - 接口名不重复
    - method/path/scope 字段齐全
    - path 中 ``{param}`` 占位符闭合配对
    - scope 取值合法（c_end / b_end）

    Raises:
        ValueError: 契约表存在上述任一错误。
    """
    seen: set[str] = set()
    valid_scopes = {"c_end", "b_end"}
    valid_methods = {"GET", "POST", "PUT", "PATCH", "DELETE"}

    for name, cfg in JAVA_API_MAP.items():
        if name in seen:
            raise ValueError(f"接口契约重复: {name}")
        seen.add(name)

        method = cfg.get("method")
        path = cfg.get("path")
        scope = cfg.get("scope")
        for field, value in (("method", method), ("path", path), ("scope", scope)):
            if not value:
                raise ValueError(f"接口 {name} 缺少字段: {field}")
        if method not in valid_methods:
            raise ValueError(f"接口 {name} 非法 method: {method}")
        if scope not in valid_scopes:
            raise ValueError(f"接口 {name} 非法 scope: {scope}")
        if not path or path.count("{") != path.count("}"):
            raise ValueError(f"接口 {name} 的 path 占位符未配对: {path}")


def validate_tool_references() -> None:
    """交叉校验：工具文件引用的接口名必须在契约表中（fail-fast）。

    扫描 ``app/mcp_server/tools/*.py`` 里 ``tool_name=`` / ``api_name=`` 引用的
    接口名，反向检查每个引用名都存在于 ``JAVA_API_MAP``。这样工具文件里接口名
    拼错会在启动时暴露，而不是运行期返回 ``API_CONTRACT_ERROR`` 静默降级。

    Raises:
        ValueError: 工具文件引用了契约表中不存在的接口名。
    """
    import re
    from pathlib import Path

    tools_dir = Path(__file__).resolve().parent.parent / "mcp_server" / "tools"
    if not tools_dir.is_dir():
        raise FileNotFoundError(f"工具目录不存在: {tools_dir}")

    # 匹配 tool_name="X" 或 api_name="Y"
    pattern = re.compile(r'(?:tool_name|api_name)\s*=\s*"([^"]+)"')
    errors: list[str] = []

    for py_file in sorted(tools_dir.glob("*.py")):
        text = py_file.read_text(encoding="utf-8")
        for match in pattern.finditer(text):
            api_name = match.group(1)
            if api_name not in JAVA_API_MAP:
                errors.append(f"{py_file.name}: 引用了未定义的接口 {api_name}")

    if errors:
        raise ValueError("工具文件引用了契约表中不存在的接口:\n  " + "\n  ".join(errors))
