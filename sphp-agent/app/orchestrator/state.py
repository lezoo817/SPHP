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
    # 元素为 dict（OpenAI 格式）或 LangChain BaseMessage，故用 Any
    messages: Annotated[list[Any], add_messages]

    # 会话唯一标识，首次对话时生成，随首个 done 事件返回前端
    session_id: str | None

    # 当前识别的业务意图：triage / registration / consultation / pharmacy / qa / chitchat
    intent: str | None

    # 从 JWT 鉴权获得的用户 ID，MCP 调用时注入 Header X-User-Id
    user_id: int | None

    # 当前服务端：c_end / b_end
    scope: str

    # B 端用户角色（ADMIN / DEPT_HEAD / DOCTOR），由 auth_node 从 B 端 token/parse 写入
    roles: list[str] | None

    # B 端用户所属科室 ID，由 auth_node 写入
    dept_id: int | None

    # B 端用户关联的医生 ID（b_doctor.id），由 auth_node 写入
    doctor_id: int | None

    # B 端医院 ID / C 端 context.hospital_id
    hospital_id: int | None

    # LLM 决定调用的工具列表，由 tool_caller 节点写入
    tool_calls: list[dict[str, Any]] | None

    # 工具执行结果列表（含成功和失败），由 tool_executor 写入
    tool_results: list[dict[str, Any]] | None

    # 待用户确认的 L2 操作列表，非空时 reply_node 推送 card 事件
    pending_confirmations: list[dict[str, Any]] | None

    # 本轮 RAG 检索到的医学知识上下文（不入 messages 历史，仅本次回复使用）
    rag_context: str | None

    # 风险标记，由 safety_check 节点追加
    risk_flags: list[str]

    # JWT Token（从请求 Header 提取，不含 "Bearer " 前缀），供 auth_node 调用 Java token/parse
    jwt_token: str | None

    # 工具调用迭代计数，子图循环用，防止无限循环
    tool_iteration: int | None
