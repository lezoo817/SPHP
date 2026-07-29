"""统一请求模型。

所有路由的入参模型集中定义于此，避免散落在各 route 文件中。
"""

from pydantic import BaseModel


class ChatRequest(BaseModel):
    """对话请求。"""
    session_id: str
    message: str
    scope: str = "c_end"           # c_end / b_end
    user_id: int | None = None
    token: str | None = None       # JWT（调后端 API 时透传）


class ConfirmRequest(BaseModel):
    """确认请求。"""
    session_id: str
    confirm_token: str
    action: str                    # confirm / cancel
    tool_name: str
    params: dict
    user_id: int | None = None
    token: str | None = None       # JWT
