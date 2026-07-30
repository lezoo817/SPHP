"""回复生成节点（系分 §5.12）。

汇总所有上游节点输出，流式推送 SSE 事件。
"""

from app.orchestrator.state import AgentState
from app.engine.llm.factory import build_llm


async def reply_node(state: AgentState) -> dict:
    """汇总工具结果或 LLM 输出，流式推送 SSE message + done。"""
    # TODO: 接入 LangGraph astream_events → SSE 事件映射
    # 1. 如果有待确认的 L2 操作，推送 card 事件
    # 2. 流式推送 message 事件
    # 3. 推送 done 事件
    return {}


def format_sse(event: str, data: dict) -> str:
    """格式化 SSE 事件。"""
    import json
    return f"event: {event}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"
