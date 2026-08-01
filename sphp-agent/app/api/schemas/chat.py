"""对话请求/响应模型（系分 §6.2）。

合并了原 request.py + response.py，集中定义对话相关的 Pydantic 模型。
"""

from pydantic import BaseModel, Field


class ChatRequest(BaseModel):
    """对话请求（系分 §6.2.1）。

    前端发起对话时传入用户消息、服务端标识和可选会话上下文。
    """
    content: str = Field(..., min_length=1, max_length=2000, description="用户输入文本，1-2000 字")
    scope: str = Field("c_end", description="服务端：c_end（患者端）/ b_end（医生端）")
    session_id: str | None = Field(None, description="会话 ID；为空时 Agent 创建新会话并在首个 done 事件中返回")
    context: dict | None = Field(None, description="附加上下文，帮助 Agent 理解当前页面状态")


class ConfirmRequest(BaseModel):
    """L2 确认回调请求（系分 §6.2.2）。"""
    confirm_token: str = Field(..., description="Agent 在 card 事件中下发的确认令牌")
    session_id: str = Field(..., description="当前对话会话 ID")


class ChatResponse(BaseModel):
    """对话响应（非 SSE 场景，如错误响应）-- 统一信封。"""
    code: str = "00000"
    message: str = "success"
    data: dict | None = None
    traceId: str = ""


class ConfirmResponse(BaseModel):
    """L2 确认回调响应（系分 §6.2.2）-- 统一信封。"""
    code: str = "00000"
    message: str = "success"
    data: dict | None = None
    traceId: str = ""


class ErrorResponse(BaseModel):
    """统一错误响应 -- 统一信封（code 为字符串错误码）。"""
    code: str
    message: str
    data: None = None
    traceId: str = ""


class HealthResponse(BaseModel):
    """健康检查响应（系分 §4.5）。"""
    status: str
    checks: dict
    version: str
