"""滑动窗口截断（系分 §5.9 对话记忆）。

控制注入 LLM 的对话上下文长度，避免超出 token 限制。
保留最近 N 轮对话（一轮 = 用户消息 + AI 回复）。
"""

from typing import Any


def _is_system(msg: object) -> bool:
    """判断是否为系统消息（兼容 dict 与 BaseMessage 两种格式）。"""
    if isinstance(msg, dict):
        return msg.get("role") == "system"
    from langchain_core.messages import SystemMessage

    return isinstance(msg, SystemMessage)


def truncate_messages(messages: list[Any], max_turns: int = 10) -> list[Any]:
    """保留最近 N 轮对话。

    Args:
        messages: 消息列表（dict 或 BaseMessage 混合格式）。
        max_turns: 保留的轮次数（一轮 = 用户消息 + AI 回复）。

    Returns:
        截断后的消息列表（系统消息始终保留在最前面）。
    """
    system_msgs = [m for m in messages if _is_system(m)]
    non_system = [m for m in messages if not _is_system(m)]
    truncated = non_system[-(max_turns * 2) :]
    return system_msgs + truncated
