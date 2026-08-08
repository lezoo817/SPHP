"""对话请求/响应模型（系分 §6.2）。

合并了原 request.py + response.py，集中定义对话相关的 Pydantic 模型。
"""

from typing import Any, Literal

from pydantic import BaseModel, Field


class ChatRequest(BaseModel):
    """对话请求（系分 §6.2.1）。

    前端发起对话时传入用户消息、服务端标识和可选会话上下文。
    """

    content: str = Field(..., min_length=1, max_length=2000, description="用户输入文本，1-2000 字")
    scope: Literal["c_end", "b_end"] = Field(
        "c_end", description="服务端：c_end（患者端）/ b_end（医生端）"
    )
    session_id: str | None = Field(
        None, description="会话 ID；为空时 Agent 创建新会话并在首个 done 事件中返回"
    )
    context: dict[str, Any] | None = Field(
        None, description="附加上下文，帮助 Agent 理解当前页面状态"
    )


class ConfirmRequest(BaseModel):
    """L2 确认回调请求（系分 §6.2.2）。"""

    confirm_token: str = Field(..., description="Agent 在 card 事件中下发的确认令牌")
    session_id: str = Field(..., description="当前对话会话 ID")
    login_password: str | None = Field(
        None,
        description="已支付订单取消所需的登录密码（仅 cancel_appointment 使用）；"
        "不进 LLM 工具 schema 与 Redis tool_arguments，仅确认时由用户输入透传",
    )


class ConfirmResponse(BaseModel):
    """L2 确认回调响应（系分 §6.2.2）-- 统一信封。"""

    code: str = "00000"
    message: str = "success"
    data: dict[str, Any] | None = None
    traceId: str = ""  # noqa: N815  # 对外统一信封契约，保持 camelCase
