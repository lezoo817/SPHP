"""通知工具封装（系分 §5.3）。

MCP 工具：manage_notifications
对应 Java API: /api/c/v1/notifications
"""

from app.infrastructure.java_client import call_java_api


async def manage_notifications(action: str, notification_id: int | None = None, user_id: int | None = None) -> dict:
    """管理通知（action=list 查列表，action=read 标已读）。"""
    if action == "read":
        if notification_id is None:
            raise ValueError("action=read 时 notification_id 必填")
        return await call_java_api("POST", f"/api/c/v1/notifications/{notification_id}/read", user_id=user_id, scope="c_end")
    return await call_java_api("GET", "/api/c/v1/notifications", user_id=user_id, scope="c_end")


def register(server):
    """注册工具到 MCP Server。"""
    pass
