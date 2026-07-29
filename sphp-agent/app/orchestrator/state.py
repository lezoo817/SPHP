"""LangGraph 主图共享状态定义。

节点返回部分 dict 做局部更新（经 reducer 合并）。
"""

from typing import Annotated, Any

from langgraph.graph import add_messages
from typing_extensions import TypedDict


class AgentState(TypedDict):
    """主图共享状态。"""

    # 对话消息列表（LangGraph 内置 reducer，append 语义）
    messages: Annotated[list, add_messages]

    # 当前识别的业务意图：triage(导诊) / registration(挂号) / consultation(问诊) / pharmacy(购药) / ...
    intent: str | None

    # 当前用户上下文（userId、role、sessionId 等）
    user_context: dict[str, Any]

    # 待确认的工具调用（L2 操作需要二次确认时暂存于此）
    pending_tool: dict[str, Any] | None

    # 风险标记（如命中高危症状、药物过敏等）
    risk_flags: list[str]

    # 当前服务的端：c_end / b_end
    scope: str | None
