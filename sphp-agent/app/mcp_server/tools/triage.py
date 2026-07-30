"""导诊工具封装（系分 §5.3）。

MCP 工具：create_triage_assessment
对应 Java API: POST /api/c/v1/triage/assessments
"""

from app.infrastructure.java_client import call_java_api


async def create_triage_assessment(
    hospital_id: int,
    symptom: str,
    duration: str | None = None,
    temperature: float | None = None,
    medical_history: str | None = None,
    user_id: int | None = None,
) -> dict:
    """提交症状进行导诊评估，返回紧急程度与推荐科室。

    Args:
        hospital_id: 医院ID
        symptom: 主要症状描述
        duration: 症状持续时间（选填）
        temperature: 体温（选填）
        medical_history: 既往病史（选填）
        user_id: 用户ID（由编排层注入）

    Returns:
        Java 后端返回的导诊评估结果
    """
    body = {"symptom": symptom}
    if duration:
        body["duration"] = duration
    if temperature is not None:
        body["temperature"] = temperature
    if medical_history:
        body["medical_history"] = medical_history

    return await call_java_api(
        method="POST",
        path=f"/api/c/v1/triage/assessments",
        body=body,
        user_id=user_id,
        scope="c_end",
    )


def register(server):
    """注册工具到 MCP Server。"""
    # TODO: 使用 @server.call_tool() 注册
    pass
