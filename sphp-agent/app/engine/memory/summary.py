"""摘要压缩（系分 §5.9 对话记忆）。

当对话轮次超过窗口大小时触发摘要压缩，避免直接丢弃旧消息丢失上下文。
截断函数 ``truncate_messages`` 见 buffer.py（本模块不重复定义）。
"""

import logging

from langchain_core.messages import HumanMessage, SystemMessage

from app.engine.llm.factory import build_llm

logger = logging.getLogger(__name__)


def _message_content(message) -> str:
    """提取消息文本，兼容 dict（OpenAI 格式）与 BaseMessage。"""
    if isinstance(message, dict):
        return message.get("content", "")
    return getattr(message, "content", str(message))


async def compress_messages(messages: list, window_size: int = 10) -> list:
    """当消息超过窗口大小触发摘要压缩。

    Args:
        messages: 当前消息列表（含 HumanMessage / AIMessage / ToolMessage）。
        window_size: 对话记忆窗口轮数（默认 10）。

    Returns:
        压缩后的消息列表。
    """
    # 触发条件：超过窗口大小 * 2（即超过 20 条消息时）
    if len(messages) <= window_size * 2:
        return messages

    # 取最早的 window_size 条消息进行压缩（数量随窗口派生，不再硬编码 10）
    to_compress = messages[:window_size]
    remaining = messages[window_size:]

    # 用 isinstance 判断角色，兼容 ToolMessage 打破 i%2 假设
    conversation_text = "\n".join(
        f"{'用户' if isinstance(m, HumanMessage) else 'AI'}: {_message_content(m)}"
        for m in to_compress
    )

    summary_prompt = (
        "请将以下对话压缩为 1-2 句摘要，保留关键信息（症状、已推荐科室、用户选择等）：\n\n"
        f"{conversation_text}\n\n"
        "摘要格式：[对话摘要] ..."
    )

    try:
        llm = build_llm(temperature=0.0)
        response = await llm.ainvoke(summary_prompt)
        summary_text = _message_content(response)

        # 在消息列表头部插入摘要作为系统消息
        summary_msg = SystemMessage(content=f"[对话摘要] {summary_text}")
        return [summary_msg] + remaining

    except Exception:
        # LLM 不可用时降级为截断，记录异常便于排查
        logger.exception("摘要压缩失败，降级为截断旧消息")
        return remaining
