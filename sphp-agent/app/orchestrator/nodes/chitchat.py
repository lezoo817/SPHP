"""闲聊对话节点（系分 §5.2.3，M6-A2）。

区分闲聊与知识问答：chitchat 场景**不触发** RAG 医疗知识检索，也不注入
医学上下文；由 reply_node 依据 ``intent=chitchat`` 以日常口吻回复。
"""

import logging
from typing import Any

from app.orchestrator.state import AgentState

logger = logging.getLogger(__name__)


async def chitchat_node(state: AgentState) -> dict[str, Any]:
    """闲聊节点：仅消费对话历史，不检索知识库。

    与 rag_node 的分工：RAG 检索面向医疗知识问答；闲聊（问候、感谢、
    寒暄等）无医疗信息需求，检索反而可能注入无关医学上下文误导 LLM。

    Args:
        state: 当前图状态，含 messages 对话历史。

    Returns:
        dict: 显式清空 ``rag_context``（防御性，防跨轮残留医学知识），
            无其他状态变更；回复由 reply_node 生成。
    """
    return {"rag_context": None}
