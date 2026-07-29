"""SSE 流式输出工具。

将 LangChain/LangGraph 的流式 chunk 格式化为 SSE 事件。
接入 sse-starlette 后使用。
"""

from typing import AsyncIterator


async def format_sse(chunks: AsyncIterator) -> AsyncIterator[str]:
    """将 LLM 流式 chunk 格式化为 SSE。

    Args:
        chunks: LangChain astream_events() 返回的 chunk 迭代器。

    Yields:
        "data: <json>\n\n" 格式的 SSE 事件。
    """
    async for chunk in chunks:
        # TODO: 接入 LangGraph astream_events 后实现
        yield f"data: {chunk}\n\n"
    yield "data: [DONE]\n\n"
