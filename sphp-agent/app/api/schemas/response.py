"""统一响应模型。"""

from pydantic import BaseModel


class ChatResponse(BaseModel):
    """对话回复。

    当 Agent 需要二次确认时，confirmation 字段附带确认卡片数据。
    """
    session_id: str
    reply: str
    confirmation: dict | None = None


class ConfirmResponse(BaseModel):
    """确认结果。"""
    session_id: str
    status: str                    # success / error / cancelled
    data: dict | None = None
    message: str | None = None
