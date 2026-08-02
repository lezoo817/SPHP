"""通知工具封装（系分 §5.3）。

MCP 工具：manage_notifications
对应 Java API: /api/c/v1/notifications
接口路径统一由 java_api_map 契约表解析。
"""

from app.infrastructure.java_client import call_java_api


async def manage_notifications(
    action: str, notification_id: int | None = None, user_id: int | None = None
) -> dict:
    """管理通知（action=list 查列表，action=read 标已读）。"""
    if action == "read":
        if notification_id is None:
            raise ValueError("action=read 时 notification_id 必填")
        return await call_java_api(
            api_name="manage_notifications:read",
            path_params={"notification_id": notification_id},
            user_id=user_id,
        )
    return await call_java_api(api_name="manage_notifications:list", user_id=user_id)


