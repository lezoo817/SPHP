"""问诊工具封装（系分 §5.3）。

MCP 工具：save_pre_consultation, query_consultations, send_consultation_message
对应 Java API: /api/c/v1/consultations 等
接口路径统一由 java_api_map 契约表解析。
"""

from app.infrastructure.java_client import call_java_api


async def save_pre_consultation(
    appointment_id: int, chief_complaint: str, submit: bool, user_id: int | None = None
) -> dict:
    """提交预问诊摘要（submit=true 提交，false 存草稿）。"""
    body = {"appointment_id": appointment_id, "chief_complaint": chief_complaint, "submit": submit}
    return await call_java_api(tool_name="save_pre_consultation", body=body, user_id=user_id)


async def query_consultations(
    consultation_id: int | None = None, status: str | None = None, user_id: int | None = None
) -> dict:
    """查询问诊记录列表或详情。"""
    if consultation_id:
        return await call_java_api(
            api_name="query_consultations:detail",
            path_params={"consultation_id": consultation_id},
            user_id=user_id,
        )
    params = {}
    if status:
        params["status"] = status
    return await call_java_api(api_name="query_consultations:list", params=params, user_id=user_id)


async def send_consultation_message(
    consultation_id: int, content: str, user_id: int | None = None
) -> dict:
    """发送问诊文字消息。"""
    body = {"content": content}
    return await call_java_api(
        api_name="send_consultation_message",
        path_params={"consultation_id": consultation_id},
        body=body,
        user_id=user_id,
    )


