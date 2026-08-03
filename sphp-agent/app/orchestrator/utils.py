"""编排层共享工具函数。"""

from app.orchestrator.state import AgentState


def get_last_user_content(state: AgentState) -> str:
    """提取最近一条用户消息的文本内容。

    兼容 dict（OpenAI 格式 ``{"role":..,"content":..}``）与 LangChain BaseMessage，
    避免 messages 列表格式不统一时取到 ``str(dict)`` 垃圾输入。
    """
    messages = state.get("messages") or []
    if not messages:
        return ""
    last = messages[-1]
    if isinstance(last, dict):
        # content 可能为 None / 非字符串（OpenAI 多段内容列表），统一转 str
        return str(last.get("content", ""))
    return getattr(last, "content", str(last))
