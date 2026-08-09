"""病历解读工具封装。

MCP 工具：interpret_medical_record
对应 Java API：/api/c/v1/medical-records/{consultId}、/health-record
"""

from typing import Any

from app.infrastructure.java_client import call_java_api


def _is_success_envelope(result: dict[str, Any]) -> bool:
    """判断 Java 统一响应是否为成功信封。

    Args:
        result: Java REST API 返回结果。

    Returns:
        成功码为 00000 时返回 True。
    """
    return result.get("code") == "00000"


def _invalid_medical_record(message: str, trace_id: str = "") -> dict[str, Any]:
    """构造病历内容不完整时的结构化失败信封。

    Args:
        message: 面向用户的失败说明。
        trace_id: 原 Java 响应的追踪编号。

    Returns:
        供工具执行器识别的失败信封。
    """
    return {"code": "A0400", "message": message, "data": None, "traceId": trace_id}


async def query_medical_records(
    patient_id: int | None = None,
    recent_days: int | None = None,
    user_id: int | None = None,
) -> dict[str, Any]:
    """查询可访问就诊人的医生病历列表。

    Args:
        patient_id: 可选就诊人 ID，未传时由 Java 查询本人。
        recent_days: 可选最近天数，仅受控记录选择入口传入 30。
        user_id: 经 JWT 鉴权得到的当前用户 ID。

    Returns:
        Java 返回的病历分页响应。
    """
    params: dict[str, Any] = {}
    if patient_id is not None:
        params["patient_id"] = patient_id
    if recent_days is not None:
        params["recent_days"] = recent_days
    return await call_java_api(
        api_name="query_medical_records:list", params=params, user_id=user_id
    )


async def interpret_medical_record(consult_id: int, user_id: int | None = None) -> dict[str, Any]:
    """读取医生病历和所属就诊人的过敏史、既往史，供 AI 生成解读。

    Args:
        consult_id: 完成问诊记录 ID，即病历 ID。
        user_id: 经 JWT 鉴权得到的当前用户 ID。

    Returns:
        仅含医生病历正文、医生信息、过敏史和既往史的成功信封；Java 读取失败时原样返回。
    """
    # 先由 Java 按病历反查患者归属并校验当前用户访问权限。
    medical_record = await call_java_api(
        api_name="query_medical_record:detail",
        path_params={"consult_id": consult_id},
        user_id=user_id,
    )
    if not _is_success_envelope(medical_record):
        return medical_record

    detail = medical_record.get("data")
    if not isinstance(detail, dict):
        return _invalid_medical_record(
            "病历详情格式异常，暂无法解读", medical_record.get("traceId", "")
        )
    patient_id = detail.get("patientId")
    doctor_note = detail.get("doctorNote")
    if isinstance(patient_id, bool) or not isinstance(patient_id, int) or patient_id <= 0:
        return _invalid_medical_record(
            "病历未提供有效就诊人信息，暂无法解读", medical_record.get("traceId", "")
        )
    if not isinstance(doctor_note, str) or not doctor_note.strip():
        return _invalid_medical_record(
            "病历正文为空，暂无法解读", medical_record.get("traceId", "")
        )

    # 病历所属患者是唯一权威来源；不使用前端页面残留的 patient_id，也不允许降级查询本人。
    health_record = await call_java_api(
        tool_name="query_health_record",
        params={"patient_id": patient_id},
        user_id=user_id,
    )
    if not _is_success_envelope(health_record):
        return health_record

    health_data = health_record.get("data")
    if not isinstance(health_data, dict):
        return _invalid_medical_record(
            "健康档案格式异常，暂无法结合病历解读", health_record.get("traceId", "")
        )

    # 只返回解读需要的病历与健康档案字段，避免将基础资料等无关敏感数据注入模型。
    return {
        "code": "00000",
        "message": "已获取病历与健康档案，正在生成解读",
        "data": {
            "medical_record": {
                "doctorName": detail.get("doctorName"),
                "departmentName": detail.get("departmentName"),
                "doctorNote": doctor_note.strip(),
                "startedAt": detail.get("startedAt"),
                "completedAt": detail.get("completedAt"),
            },
            "health_record": {
                "allergies": health_data.get("allergies") or [],
                "medicalHistories": health_data.get("medicalHistories") or [],
            },
        },
        "traceId": medical_record.get("traceId", health_record.get("traceId", "")),
    }
