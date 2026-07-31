"""LangGraph 主图共享状态定义（系分 §7.1）。

AgentState 是图中唯一的共享状态对象，
通过 LangGraph 的 add_messages reducer 自动累积对话历史。
"""

from typing import Annotated, Any

from langgraph.graph import add_messages
from typing_extensions import TypedDict


class AgentState(TypedDict):
    """主图共享状态（系分 §7.1）。"""

    # 对话消息列表（LangGraph 内置 reducer，append 语义）
    messages: Annotated[list, add_messages]

    # 会话唯一标识，首次对话时生成，随首个 done 事件返回前端
    session_id: str | None

    # 当前识别的业务意图：triage / registration / consultation / pharmacy / qa / chitchat
    intent: str | None

    # 从 JWT 鉴权获得的用户 ID，MCP 调用时注入 Header X-User-Id
    user_id: int | None

    # 当前服务端：c_end / b_end
    scope: str

    # LLM 决定调用的工具列表，由 tool_caller 节点写入
    tool_calls: list[dict] | None

    # 工具执行结果列表（含成功和失败），由 tool_executor 写入
    tool_results: list[dict] | None

    # 待用户确认的 L2 操作信息，非空时 reply_node 推送 card 事件
    pending_confirmation: dict | None

    # 风险标记，由 safety_check 节点追加
    risk_flags: list[str]
