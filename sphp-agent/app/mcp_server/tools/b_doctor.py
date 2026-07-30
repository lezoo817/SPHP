"""B 端医生工具封装（系分 §5.3）。

MCP 工具：query_patient_history, query_drug_guide, check_drug_interaction,
          generate_draft_note, recommend_care, check_contraindication,
          check_allergy_risk, check_duplicate_medication
对应 Java API: /api/b/* （B端后端系分 V1.0）
"""

from app.infrastructure.java_client import call_java_api


async def query_patient_history(patient_id: int, user_id: int | None = None) -> dict:
    """聚合查询患者基本信息、过敏史、既往史、就诊记录、历史处方、当前用药。"""
    # 多 API 聚合
    base = await call_java_api("GET", f"/api/b/patients/{patient_id}", user_id=user_id, scope="b_end")
    visits = await call_java_api("GET", f"/api/b/patients/{patient_id}/visits", user_id=user_id, scope="b_end")
    prescriptions = await call_java_api("GET", f"/api/b/patients/{patient_id}/prescriptions", user_id=user_id, scope="b_end")
    medications = await call_java_api("GET", f"/api/b/patients/{patient_id}/medications", user_id=user_id, scope="b_end")
    return {"base_info": base, "visits": visits, "prescriptions": prescriptions, "medications": medications}


async def query_drug_guide(drug_name: str, user_id: int | None = None) -> dict:
    """查询药品说明书和用药指南。"""
    params = {"drug_name": drug_name}
    return await call_java_api("GET", "/api/b/drugs", params=params, user_id=user_id, scope="b_end")


async def check_drug_interaction(drug_names: list[str], patient_id: int, user_id: int | None = None) -> dict:
    """聚合返回药品说明书 + 患者当前用药清单。"""
    drug_info = await call_java_api("GET", "/api/b/drugs", params={"drug_names": ",".join(drug_names)}, user_id=user_id, scope="b_end")
    medications = await call_java_api("GET", f"/api/b/patients/{patient_id}/medications", user_id=user_id, scope="b_end")
    return {"drug_info": drug_info, "current_medications": medications}


async def generate_draft_note(consultation_id: int, note_content: str, user_id: int | None = None) -> dict:
    """保存医生病历记录（编排层 LLM 生成草稿文本，工具负责持久化）。"""
    body = {"note_content": note_content}
    return await call_java_api("PUT", f"/api/b/doctor/consult/{consultation_id}/note", body=body, user_id=user_id, scope="b_end")


async def recommend_care(department_id: int | None = None, user_id: int | None = None) -> dict:
    """聚合返回科室列表、医生列表及排班号源。"""
    params = {}
    if department_id:
        params["department_id"] = department_id
    departments = await call_java_api("GET", "/api/b/admin/departments", params=params, user_id=user_id, scope="b_end")
    doctors = await call_java_api("GET", "/api/b/admin/doctors", params=params, user_id=user_id, scope="b_end")
    schedules = await call_java_api("GET", "/api/b/schedules", params=params, user_id=user_id, scope="b_end")
    return {"departments": departments, "doctors": doctors, "schedules": schedules}


async def check_contraindication(drug_name: str, patient_id: int, user_id: int | None = None) -> dict:
    """聚合返回药品禁忌信息 + 患者过敏史/既往史。"""
    drug_info = await call_java_api("GET", "/api/b/drugs", params={"drug_name": drug_name}, user_id=user_id, scope="b_end")
    patient = await call_java_api("GET", f"/api/b/patients/{patient_id}", user_id=user_id, scope="b_end")
    return {"drug_info": drug_info, "patient_info": patient}


async def check_allergy_risk(drug_name: str, patient_id: int, user_id: int | None = None) -> dict:
    """返回患者过敏史记录。"""
    patient = await call_java_api("GET", f"/api/b/patients/{patient_id}", user_id=user_id, scope="b_end")
    return {"patient_info": patient}


async def check_duplicate_medication(drug_name: str, patient_id: int, user_id: int | None = None) -> dict:
    """返回患者当前用药清单。"""
    medications = await call_java_api("GET", f"/api/b/patients/{patient_id}/medications", user_id=user_id, scope="b_end")
    return {"current_medications": medications}


def register(server):
    """注册工具到 MCP Server。"""
    pass
