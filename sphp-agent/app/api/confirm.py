"""确认回调接口：用户点击确认卡片后，前端调此接口完成 L2 操作。"""

from pydantic import BaseModel
from fastapi import APIRouter

from app.tools.executor import execute

router = APIRouter()


class ConfirmRequest(BaseModel):
    """确认请求。"""
    session_id: str
    confirm_token: str
    action: str                  # confirm / cancel
    tool_name: str
    params: dict
    user_id: int | None = None
    token: str | None = None     # JWT


class ConfirmResponse(BaseModel):
    """确认结果。"""
    session_id: str
    status: str                  # success / error / cancelled
    data: dict | None = None
    message: str | None = None


@router.post("/confirm", response_model=ConfirmResponse)
async def confirm(req: ConfirmRequest) -> ConfirmResponse:
    """二次确认回调。

    前端收到 awaiting_confirmation 后渲染确认卡片，
    用户点击"确认"或"取消"后调此接口。
    """
    if req.action == "cancel":
        return ConfirmResponse(
            session_id=req.session_id,
            status="cancelled",
            message="用户取消了操作",
        )

    result = execute(
        tool_name=req.tool_name,
        params=req.params,
        user_context={
            "user_id": req.user_id,
            "session_id": req.session_id,
            "token": req.token,
        },
        confirm_token=req.confirm_token,
    )

    return ConfirmResponse(
        session_id=req.session_id,
        status=result.get("status", "error"),
        data=result.get("data"),
        message=result.get("message"),
    )
