"""摘要压缩（系分 §5.9 对话记忆）。

当对话轮次超过窗口大小时，不直接丢弃旧消息，而是触发摘要压缩：
1. 触发条件：len(messages) > window_size * 2
2. 压缩策略：取最早的 5 轮对话，调用 LLM 生成 1-2 句摘要
3. 摘要格式：[对话摘要] 用户描述了{症状}，已推荐{科室}，用户选择了{医生}...
4. 替换方式：删除原始 5 轮消息，在消息列表头部插入摘要作为系统消息
"""

from langchain_core.messages import SystemMessage

from app.engine.llm.factory import build_llm


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

    # 取最早的 5 轮对话（10 条消息）进行压缩
    to_compress = messages[:10]
    remaining = messages[10:]

    # 构造摘要 prompt
    conversation_text = "\n".join(
        f"{'用户' if i % 2 == 0 else 'AI'}: {m.content if hasattr(m, 'content') else str(m)}"
        for i, m in enumerate(to_compress)
    )

    summary_prompt = (
        "请将以下对话压缩为 1-2 句摘要，保留关键信息（症状、已推荐科室、用户选择等）：\n\n"
        f"{conversation_text}\n\n"
        "摘要格式：[对话摘要] ..."
    )

    try:
        llm = build_llm(temperature=0.0)
        response = await llm.ainvoke(summary_prompt)
        summary_text = response.content if hasattr(response, "content") else str(response)

        # 在消息列表头部插入摘要作为系统消息
        summary_msg = SystemMessage(content=f"[对话摘要] {summary_text}")
        return [summary_msg] + remaining

    except Exception:
        # LLM 不可用时直接截断，不压缩
        return remaining


def truncate_messages(messages: list, max_turns: int = 10) -> list:
    """保留最近 N 轮对话。

    Args:
        messages: LangGraph 消息列表。
        max_turns: 保留的轮次数（一轮 = 用户消息 + AI 回复）。

    Returns:
        截断后的消息列表。
    """
    return messages[-(max_turns * 2):]
