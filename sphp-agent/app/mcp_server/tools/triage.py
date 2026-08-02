"""导诊工具封装（系分 §5.3）。

MCP 工具：create_triage_assessment
对应 Java API: POST /api/c/v1/triage/assessments
"""

import logging

from app.infrastructure.java_client import call_java_api

logger = logging.getLogger(__name__)


async def create_triage_assessment(
    hospital_id: int,
    symptom: str,
    duration: str | None = None,
    temperature: float | None = None,
    medical_history: str | None = None,
    user_id: int | None = None,
) -> dict:
    """提交症状进行导诊评估，返回紧急程度与推荐科室（系分 §5.3）。

    Args:
        hospital_id: 医院ID
        symptom: 主要症状描述
        duration: 症状持续时间（选填）
        temperature: 体温（选填）
        medical_history: 既往病史（选填）
        user_id: 用户ID（由编排层注入）

    Returns:
        dict: Java后端返回的导诊评估结果，包含：
            - urgency_level: 紧急程度（1-5）
            - recommended_department: 推荐科室
            - recommended_doctors: 推荐医生列表

    Raises:
        httpx.HTTPError: Java API调用失败
    """
    body = {"hospital_id": hospital_id, "symptom": symptom}
    if duration:
        body["duration"] = duration
    if temperature is not None:
        body["temperature"] = temperature
    if medical_history:
        body["medical_history"] = medical_history

    logger.info("导诊评估请求: hospital_id=%s, symptom_len=%d", hospital_id, len(symptom))

    return await call_java_api(
        method="POST",
        path="/api/c/v1/triage/assessments",
        body=body,
        user_id=user_id,
        scope="c_end",
    )


def register(server):
    """注册导诊工具到 MCP Server（系分 §4.5）。

    Args:
        server: MCP Server实例

    注册的工具：
    - create_triage_assessment: 创建导诊评估

    注意：当前MCP SDK版本不直接支持装饰器注册，
    工具函数通过tool_caller节点显式调用。
    """
    # 当前版本：工具函数已实现，由编排层直接调用
    # MCP工具注册将在SDK版本稳定后完善
    logger.info("导诊工具函数已就绪: create_triage_assessment")
