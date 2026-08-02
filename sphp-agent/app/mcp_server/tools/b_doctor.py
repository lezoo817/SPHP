"""B 端医生工具封装（系分 §5.3）。

MCP 工具：query_patient_history, query_drug_guide, check_drug_interaction,
          generate_draft_note, recommend_care, check_contraindication,
          check_allergy_risk, check_duplicate_medication
对应 Java API: /api/b/* （B端后端系分 V1.0）
接口路径统一由 java_api_map 契约表解析。
"""

from app.infrastructure.java_client import call_java_api


async def query_patient_history(patient_id: int, user_id: int | None = None) -> dict:
    """聚合查询患者基本信息、过敏史、既往史、就诊记录、历史处方、当前用药。"""
    # 多 API 聚合（各子接口路径由契约表解析）
    base = await call_java_api(
        api_name="query_patient_history:base",
        path_params={"patient_id": patient_id},
        user_id=user_id,
    )
    visits = await call_java_api(
        api_name="query_patient_history:visits",
        path_params={"patient_id": patient_id},
        user_id=user_id,
    )
    prescriptions = await call_java_api(
        api_name="query_patient_history:prescriptions",
        path_params={"patient_id": patient_id},
        user_id=user_id,
    )
    medications = await call_java_api(
        api_name="query_patient_history:medications",
        path_params={"patient_id": patient_id},
        user_id=user_id,
    )
    return {
        "base_info": base,
        "visits": visits,
        "prescriptions": prescriptions,
        "medications": medications,
    }


async def query_drug_guide(drug_name: str, user_id: int | None = None) -> dict:
    """查询药品说明书和用药指南。"""
    params = {"drug_name": drug_name}
    return await call_java_api(tool_name="query_drug_guide", params=params, user_id=user_id)


async def check_drug_interaction(
    drug_names: list[str], patient_id: int, user_id: int | None = None
) -> dict:
    """聚合返回药品说明书 + 患者当前用药清单。

    逐个药品查说明书（Java 接口用单数 drug_name，与 query_drug_guide 一致），
    避免传错参数名导致查不到数据。
    """
    drug_info = await call_java_api(
        api_name="query_drug_guide",
        params={"drug_name": ",".join(drug_names)},
        user_id=user_id,
    )
    medications = await call_java_api(
        api_name="query_patient_medications",
        path_params={"patient_id": patient_id},
        user_id=user_id,
    )
    return {"drug_info": drug_info, "current_medications": medications}


async def generate_draft_note(
    consultation_id: int, note_content: str, user_id: int | None = None
) -> dict:
    """保存医生病历记录（编排层 LLM 生成草稿文本，工具负责持久化）。"""
    body = {"note_content": note_content}
    return await call_java_api(
        api_name="generate_draft_note",
        path_params={"consultation_id": consultation_id},
        body=body,
        user_id=user_id,
    )


async def recommend_care(department_id: int | None = None, user_id: int | None = None) -> dict:
    """聚合返回科室列表、医生列表及排班号源。"""
    params = {}
    if department_id:
        params["department_id"] = department_id
    departments = await call_java_api(
        api_name="recommend_care:departments", params=params, user_id=user_id
    )
    doctors = await call_java_api(api_name="recommend_care:doctors", params=params, user_id=user_id)
    schedules = await call_java_api(
        api_name="recommend_care:schedules", params=params, user_id=user_id
    )
    return {"departments": departments, "doctors": doctors, "schedules": schedules}


async def check_contraindication(
    drug_name: str, patient_id: int, user_id: int | None = None
) -> dict:
    """聚合返回药品禁忌信息 + 患者过敏史/既往史。"""
    drug_info = await call_java_api(
        api_name="query_drug_guide", params={"drug_name": drug_name}, user_id=user_id
    )
    patient = await call_java_api(
        api_name="query_patient_history:base",
        path_params={"patient_id": patient_id},
        user_id=user_id,
    )
    return {"drug_info": drug_info, "patient_info": patient}


async def check_allergy_risk(drug_name: str, patient_id: int, user_id: int | None = None) -> dict:
    """返回患者过敏史记录。"""
    patient = await call_java_api(
        api_name="query_patient_history:base",
        path_params={"patient_id": patient_id},
        user_id=user_id,
    )
    return {"patient_info": patient}


async def check_duplicate_medication(
    drug_name: str, patient_id: int, user_id: int | None = None
) -> dict:
    """返回患者当前用药清单。"""
    medications = await call_java_api(
        api_name="query_patient_medications",
        path_params={"patient_id": patient_id},
        user_id=user_id,
    )
    return {"current_medications": medications}


