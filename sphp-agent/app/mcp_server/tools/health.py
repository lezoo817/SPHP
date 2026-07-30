"""健康管理工具封装（系分 §5.3）。

MCP 工具：query_health_record, manage_allergy, manage_medical_history,
          query_reports, create_report, query_medication_plans,
          update_medication_plan, query_follow_ups, confirm_follow_up
对应 Java API: /api/c/v1/health-record, /reports, /medication-plans, /follow-ups 等
"""

from app.infrastructure.java_client import call_java_api


async def query_health_record(patient_id: int | None = None, user_id: int | None = None) -> dict:
    """查询健康档案（含过敏史、既往史）。"""
    params = {}
    if patient_id:
        params["patient_id"] = patient_id
    return await call_java_api("GET", "/api/c/v1/health-record", params=params, user_id=user_id, scope="c_end")


async def manage_allergy(allergen: str, allergy_id: int | None = None, reaction: str | None = None, user_id: int | None = None) -> dict:
    """管理过敏史记录（不带 allergy_id 新增，带 allergy_id 修改）。"""
    body = {"allergen": allergen}
    if reaction:
        body["reaction"] = reaction
    if allergy_id:
        return await call_java_api("PUT", f"/api/c/v1/health-record/allergies/{allergy_id}", body=body, user_id=user_id, scope="c_end")
    return await call_java_api("POST", "/api/c/v1/health-record/allergies", body=body, user_id=user_id, scope="c_end")


async def manage_medical_history(content: str, history_id: int | None = None, occurred_at: str | None = None, user_id: int | None = None) -> dict:
    """管理既往史记录（不带 history_id 新增，带 history_id 修改）。"""
    body = {"content": content}
    if occurred_at:
        body["occurred_at"] = occurred_at
    if history_id:
        return await call_java_api("PUT", f"/api/c/v1/health-record/histories/{history_id}", body=body, user_id=user_id, scope="c_end")
    return await call_java_api("POST", "/api/c/v1/health-record/histories", body=body, user_id=user_id, scope="c_end")


async def query_reports(report_id: int | None = None, user_id: int | None = None) -> dict:
    """查询检查报告列表或详情（带 report_id 时包含指标解读）。"""
    if report_id:
        # 查详情 + 指标解读
        detail = await call_java_api("GET", f"/api/c/v1/reports/{report_id}", user_id=user_id, scope="c_end")
        interpretation = await call_java_api("GET", f"/api/c/v1/reports/{report_id}/interpretation", user_id=user_id, scope="c_end")
        return {"detail": detail, "interpretation": interpretation}
    return await call_java_api("GET", "/api/c/v1/reports", user_id=user_id, scope="c_end")


async def create_report(report_name: str, report_date: str, indicators: list, patient_id: int | None = None, user_id: int | None = None) -> dict:
    """录入检查报告。"""
    body = {"report_name": report_name, "report_date": report_date, "indicators": indicators}
    if patient_id:
        body["patient_id"] = patient_id
    return await call_java_api("POST", "/api/c/v1/reports", body=body, user_id=user_id, scope="c_end")


async def query_medication_plans(status: str | None = None, user_id: int | None = None) -> dict:
    """查询用药计划列表。"""
    params = {}
    if status:
        params["status"] = status
    return await call_java_api("GET", "/api/c/v1/medication-plans", params=params, user_id=user_id, scope="c_end")


async def update_medication_plan(plan_id: int, action: str, user_id: int | None = None) -> dict:
    """暂停/恢复/完成用药计划。"""
    body = {"action": action}
    return await call_java_api("PATCH", f"/api/c/v1/medication-plans/{plan_id}", body=body, user_id=user_id, scope="c_end")


async def query_follow_ups(status: str | None = None, user_id: int | None = None) -> dict:
    """查询随访计划列表。"""
    params = {}
    if status:
        params["status"] = status
    return await call_java_api("GET", "/api/c/v1/follow-ups", params=params, user_id=user_id, scope="c_end")


async def confirm_follow_up(follow_up_id: int, remind_at: str | None = None, user_id: int | None = None) -> dict:
    """确认随访提醒时间。"""
    body = {}
    if remind_at:
        body["remind_at"] = remind_at
    return await call_java_api("POST", f"/api/c/v1/follow-ups/{follow_up_id}/confirm", body=body, user_id=user_id, scope="c_end")


def register(server):
    """注册工具到 MCP Server。"""
    pass
