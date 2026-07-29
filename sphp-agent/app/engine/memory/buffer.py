"""滑动窗口截断。

控制注入 LLM 的对话上下文长度，避免超出 token 限制。
"""


def truncate_messages(messages: list, max_turns: int = 10) -> list:
    """保留最近 N 轮对话。

    Args:
        messages: LangGraph 消息列表（含 HumanMessage / AIMessage / ToolMessage）。
        max_turns: 保留的轮次数（一轮 = 用户消息 + AI 回复）。

    Returns:
        截断后的消息列表。
    """
    # TODO: 接入 LangGraph 后实现精确按轮次截断
    # 当前占位：直接返回最后 max_turns * 2 条消息
    return messages[-(max_turns * 2):]
