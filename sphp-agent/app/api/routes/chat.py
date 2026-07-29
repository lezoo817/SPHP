"""对话接口：接收用户消息，返回 Agent 回复。

当前为占位实现，后续接入 LangGraph 主图。
"""

from pydantic import BaseModel
from fastapi import APIRouter

router = APIRouter()


class ChatRequest(BaseModel):
    """对话请求。"""
    session_id: str
    message: str
    scope: str = "c_end"           # c_end / b_end
    user_id: int | None = None
    token: str | None = None       # JWT（调后端 API 时透传）


class ChatResponse(BaseModel):
    """对话回复。"""
    session_id: str
    reply: str
    # 当 Agent 需要二次确认时，附带确认卡片
    confirmation: dict | None = None


@router.post("/chat", response_model=ChatResponse)
async def chat(req: ChatRequest) -> ChatResponse:
    """对话主接口。

    流程（待实现）：
    1. 根据 scope 加载对应工具集（c_tools / b_tools）
    2. 将消息 + 工具列表传给 LangGraph 主图
    3. LLM 判断意图 → 直接回复 / 调工具
    4. 若工具返回 awaiting_confirmation → 附带确认卡片
    """
    # TODO: 接入 LangGraph
    return ChatResponse(
        session_id=req.session_id,
        reply="（Agent 服务搭建中，当前为占位回复）",
        confirmation=None,
    )
