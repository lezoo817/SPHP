"""RAG 检索节点（系分 §5.7）。

从医疗知识库检索相关内容，注入 LLM 上下文。
"""

from app.orchestrator.state import AgentState
from app.engine.rag.search import search_knowledge, format_context


async def rag_node(state: AgentState) -> dict:
    """从医疗知识库检索相关内容（系分 §5.7）。

    当前版本：暂不启用RAG检索（等待embedding API配置）。
    """
    # TODO: 启用RAG检索（需配置 SILICONFLOW_API_KEY）
    # user_message = state["messages"][-1].content if state.get("messages") else ""
    # results = search_knowledge(query=user_message)
    # context = format_context(results)
    # return {"tool_results": [{"rag_context": context}]}

    # 当前：跳过RAG，返回空结果
    return {"messages": []}
