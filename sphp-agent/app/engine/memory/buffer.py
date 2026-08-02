"""滑动窗口截断（系分 §5.9 对话记忆）。

控制注入 LLM 的对话上下文长度，避免超出 token 限制。
保留最近 N 轮对话（一轮 = 用户消息 + AI 回复）。
"""

from langchain_core.messages import SystemMessage


def truncate_messages(messages: list, max_turns: int = 10) -> list:
    """保留最近 N 轮对话。

    Args:
        messages: LangGraph 消息列表（含 HumanMessage / AIMessage / ToolMessage）。
        max_turns: 保留的轮次数（一轮 = 用户消息 + AI 回复）。

    Returns:
        截断后的消息列表。
    """
    # 保留系统消息（摘要等），再取最近 max_turns * 2 条非系统消息
    system_msgs = [m for m in messages if isinstance(m, SystemMessage)]
    non_system = [m for m in messages if not isinstance(m, SystemMessage)]
    truncated = non_system[-(max_turns * 2):]
    return system_msgs + truncated
