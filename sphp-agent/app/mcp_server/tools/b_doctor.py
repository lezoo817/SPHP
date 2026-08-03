"""B 端医生工具封装（系分 §5.3）。

MCP 工具：query_patient_history, query_drug_guide, check_drug_interaction,
          generate_draft_note, recommend_care, check_contraindication,
          check_allergy_risk, check_duplicate_medication
对应 Java API: /api/b/* （B端后端系分 V1.0）
接口路径统一由 java_api_map 契约表解析。
"""

import asyncio
from typing import Any

from app.infrastructure.java_client import call_java_api


def _as_error_dict(e: BaseException) -> dict[str, Any]:
    """聚合子调用异常兜底：转为统一失败结果（技术债 T1 部分失败隔离）。

    call_java_api 正常失败返回含 error 字段的 dict（不抛异常），此处仅防御
    非预期异常（如实现回归）导致 gather 传播中断整个聚合。
    """
    return {"success": False, "error": {"code": "AGGREGATE_FAILED", "message": str(e)}}


async def query_patient_history(patient_id: int, user_id: int | None = None) -> dict[str, Any]:
    """聚合查询患者基本信息、过敏史、既往史、就诊记录、历史处方、当前用药。

    技术债 T1：4 个子接口 asyncio.gather 并发（原串行 await），任一失败
    不影响其余子查询（部分失败隔离），由编排层 LLM 综合判断。
    """
    results = await asyncio.gather(
        call_java_api(
            api_name="query_patient_history:base",
            path_params={"patient_id": patient_id},
            user_id=user_id,
        ),
        call_java_api(
            api_name="query_patient_history:visits",
            path_params={"patient_id": patient_id},
            user_id=user_id,
        ),
        call_java_api(
            api_name="query_patient_history:prescriptions",
            path_params={"patient_id": patient_id},
            user_id=user_id,
        ),
        call_java_api(
            api_name="query_patient_history:medications",
            path_params={"patient_id": patient_id},
            user_id=user_id,
        ),
        return_exceptions=True,
    )
    base, visits, prescriptions, medications = [
        r if isinstance(r, dict) else _as_error_dict(r) for r in results
    ]
    return {
        "base_info": base,
        "visits": visits,
        "prescriptions": prescriptions,
        "medications": medications,
    }


async def query_drug_guide(drug_name: str, user_id: int | None = None) -> dict[str, Any]:
    """查询药品说明书和用药指南。"""
    params = {"drug_name": drug_name}
    return await call_java_api(tool_name="query_drug_guide", params=params, user_id=user_id)


async def check_drug_interaction(
    drug_names: list[str], patient_id: int, user_id: int | None = None
) -> dict[str, Any]:
    """聚合返回药品说明书 + 患者当前用药清单。

    技术债 T3：Java ``GET /admin/drugs`` 契约只支持单数 drug_name（与
    query_drug_guide 一致），原逗号拼接多药依赖 Java 侧扩展解析。改为逐药
    并发查询对齐单数契约，每药独立失败不影响其余药品；与患者用药查询并发。
    """
    results = await asyncio.gather(
        *[
            call_java_api(
                api_name="query_drug_guide",
                params={"drug_name": name},
                user_id=user_id,
            )
            for name in drug_names
        ],
        call_java_api(
            api_name="query_patient_medications",
            path_params={"patient_id": patient_id},
            user_id=user_id,
        ),
        return_exceptions=True,
    )
    drug_infos, medications = results[:-1], results[-1]
    return {
        "drug_info": [d if isinstance(d, dict) else _as_error_dict(d) for d in drug_infos],
        "current_medications": medications
        if isinstance(medications, dict)
        else _as_error_dict(medications),
    }


async def generate_draft_note(
    consultation_id: int, note_content: str, user_id: int | None = None
) -> dict[str, Any]:
    """保存医生病历记录（编排层 LLM 生成草稿文本，工具负责持久化）。"""
    body = {"note_content": note_content}
    return await call_java_api(
        api_name="generate_draft_note",
        path_params={"consultation_id": consultation_id},
        body=body,
        user_id=user_id,
    )


async def recommend_care(
    department_id: int | None = None, user_id: int | None = None
) -> dict[str, Any]:
    """聚合返回科室列表、医生列表及排班号源。

    技术债 T1：3 个子接口 asyncio.gather 并发（原串行 await），任一失败不影响其余。
    """
    params = {}
    if department_id:
        params["department_id"] = department_id
    results = await asyncio.gather(
        call_java_api(api_name="recommend_care:departments", params=params, user_id=user_id),
        call_java_api(api_name="recommend_care:doctors", params=params, user_id=user_id),
        call_java_api(api_name="recommend_care:schedules", params=params, user_id=user_id),
        return_exceptions=True,
    )
    departments, doctors, schedules = [
        r if isinstance(r, dict) else _as_error_dict(r) for r in results
    ]
    return {"departments": departments, "doctors": doctors, "schedules": schedules}


async def check_contraindication(
    drug_name: str, patient_id: int, user_id: int | None = None
) -> dict[str, Any]:
    """聚合返回药品禁忌信息 + 患者过敏史/既往史。

    技术债 T1：2 个子接口 asyncio.gather 并发（原串行 await），任一失败不影响其余。
    """
    results = await asyncio.gather(
        call_java_api(
            api_name="query_drug_guide", params={"drug_name": drug_name}, user_id=user_id
        ),
        call_java_api(
            api_name="query_patient_history:base",
            path_params={"patient_id": patient_id},
            user_id=user_id,
        ),
        return_exceptions=True,
    )
    drug_info, patient = [r if isinstance(r, dict) else _as_error_dict(r) for r in results]
    return {"drug_info": drug_info, "patient_info": patient}


async def check_allergy_risk(
    drug_name: str, patient_id: int, user_id: int | None = None
) -> dict[str, Any]:
    """返回患者过敏史记录。"""
    patient = await call_java_api(
        api_name="query_patient_history:base",
        path_params={"patient_id": patient_id},
        user_id=user_id,
    )
    return {"patient_info": patient}


async def check_duplicate_medication(
    drug_name: str, patient_id: int, user_id: int | None = None
) -> dict[str, Any]:
    """返回患者当前用药清单。"""
    medications = await call_java_api(
        api_name="query_patient_medications",
        path_params={"patient_id": patient_id},
        user_id=user_id,
    )
    return {"current_medications": medications}
