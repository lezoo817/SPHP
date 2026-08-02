"""回复生成节点（系分 §5.12）。

汇总所有上游节点输出，调用 LLM 生成自然语言回复。
"""

import logging
from typing import Any

from app.engine.llm.factory import build_llm
from app.engine.memory.buffer import truncate_messages
from app.infrastructure.config.settings import get_settings
from app.orchestrator.state import AgentState

logger = logging.getLogger(__name__)

# 医疗安全声明（强制注入所有回复末尾）
MEDICAL_DISCLAIMER = "\n\n---\n⚠️ **AI 建议仅供参考，不作为诊断依据。如有疑问请咨询专业医生。**"

# 回复生成系统提示词
REPLY_SYSTEM_PROMPT = """你是一个医疗健康助手，正在为用户提供服务。

当前场景：
- 用户意图：{intent}
- 服务端：{scope}

任务：
1. 根据对话历史和工具调用结果，生成自然、友好的回复
2. 如果有工具调用结果，优先基于结果回答
3. 保持回复简洁、专业，避免过度医疗建议
4. 涉及诊断、用药建议时，提醒用户咨询专业医生

注意：
- 不要编造医疗数据
- 不要替代医生做诊断
- 不确定的内容要明确告知
"""


async def reply_node(state: AgentState) -> dict[str, Any]:
    """回复生成节点（系分 §5.12）。

    汇总工具结果或 LLM 输出，调用 LLM 生成自然语言回复。
    回复末尾强制注入医疗安全声明。

    Args:
        state: 当前图状态，包含 messages / tool_results 等字段。

    Returns:
        dict: 包含新增的 assistant 消息（由 LangGraph add_messages reducer 累积）。

    Raises:
        无：生成失败时返回降级话术（含安全声明）。
    """
    try:
        llm = build_llm()

        intent = state.get("intent", "qa")
        scope = state.get("scope", "c_end")
        system_prompt = REPLY_SYSTEM_PROMPT.format(intent=intent, scope=scope)

        # 构造 LLM 输入（不修改 state.messages，避免副作用）
        llm_messages: list[dict[str, Any]] = [{"role": "system", "content": system_prompt}]
        history = truncate_messages(state.get("messages", []), get_settings().memory_window_size)
        llm_messages.extend(history)

        # 如果有 RAG 检索知识，注入到上下文（本轮有效，不写入历史）
        rag_context = state.get("rag_context")
        if rag_context:
            llm_messages.append({"role": "system", "content": rag_context})

        # 如果有工具调用结果，注入到上下文
        tool_results = state.get("tool_results")
        if tool_results:
            tool_summary = _format_tool_results(tool_results)
            llm_messages.append({"role": "system", "content": f"工具调用结果：\n{tool_summary}"})

        response = await llm.ainvoke(llm_messages)
        reply_content = response.content

        # 强制注入医疗安全声明（所有意图）
        reply_content += MEDICAL_DISCLAIMER

        logger.info("回复生成成功: 长度=%d, 意图=%s", len(reply_content), intent)
        return {"messages": [{"role": "assistant", "content": reply_content}]}

    except Exception as e:
        logger.error("回复生成失败: %s", e)
        fallback_message = "抱歉，我遇到了一些问题，请稍后重试。" + MEDICAL_DISCLAIMER
        return {"messages": [{"role": "assistant", "content": fallback_message}]}


def _format_tool_results(tool_results: list[dict]) -> str:
    """格式化工具调用结果为自然语言摘要。

    Args:
        tool_results: 工具执行结果列表。

    Returns:
        str: 自然语言格式的工具结果摘要。
    """
    summaries = []
    for result in tool_results:
        tool_name = result.get("tool_name", "未知工具")
        success = result.get("success", False)

        if success:
            data = result.get("data", {})
            summaries.append(f"✓ {tool_name}: 执行成功，获取 {len(data)} 条数据")
        else:
            error_msg = result.get("error", {}).get("message", "未知错误")
            summaries.append(f"✗ {tool_name}: 执行失败 - {error_msg}")

    return "\n".join(summaries)
