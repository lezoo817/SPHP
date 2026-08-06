"""回复生成节点（系分 §5.12）。

汇总所有上游节点输出，调用 LLM 生成自然语言回复。
"""

import json
import logging
from datetime import date
from typing import Any

from app.engine.llm.factory import build_llm
from app.engine.memory.buffer import truncate_messages
from app.infrastructure.config.settings import get_settings
from app.orchestrator.state import AgentState

logger = logging.getLogger(__name__)

# 回复生成系统提示词
REPLY_SYSTEM_PROMPT = """你是一个医疗健康助手，正在为用户提供服务。

当前日期：{today}（服务器本地日期，YYYY-MM-DD）

当前场景：
- 用户意图：{intent}
- 服务端：{scope}

任务：
1. 根据对话历史和工具调用结果，生成自然、友好的回复
2. 如果有工具调用结果，优先基于结果回答
3. 保持回复简洁、专业，避免过度医疗建议
4. 涉及诊断、用药建议时，提醒用户咨询专业医生
5. 涉及日期/排班信息时，以当前日期 {today} 为参照，不得混淆或编造日期

注意：
- 不要编造医疗数据
- 不要替代医生做诊断
- 不确定的内容要明确告知
"""


async def reply_node(state: AgentState) -> dict[str, Any]:
    """回复生成节点（系分 §5.12）。

    汇总工具结果或 LLM 输出，调用 LLM 生成自然语言回复。
    医疗免责声明由前端卡片统一承担，此处不再注入。

    Args:
        state: 当前图状态，包含 messages / tool_results 等字段。

    Returns:
        dict: 包含新增的 assistant 消息（由 LangGraph add_messages reducer 累积）。

    Raises:
        无：生成失败时返回降级话术。
    """
    try:
        llm = build_llm()

        intent = state.get("intent", "qa")
        scope = state.get("scope", "c_end")
        system_prompt = REPLY_SYSTEM_PROMPT.format(
            intent=intent, scope=scope, today=date.today().isoformat()
        )

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

        # 如果有待确认的 L2 操作，提醒 LLM 在回复中引导用户查看确认卡片
        pending_confirmations = state.get("pending_confirmations")
        if pending_confirmations:
            tool_names = [p.get("tool_name", "") for p in pending_confirmations]
            prompt = (
                f"有 {len(pending_confirmations)} 个操作（{', '.join(tool_names)}）正在等待用户"
                "点击确认卡片，**操作尚未执行**。请用简短回复说明用户即将执行的操作，"
                "并引导用户查看并点击确认卡片。"
                "⚠️ 严禁声称操作已成功/已提交/已发送/已收到——确认卡片点击后才会真正执行，"
                "确认前一律用'即将/待确认'表述，不要替用户完成操作。"
            )
            llm_messages.append({"role": "system", "content": prompt})

        # M8-7：选医生卡片已就绪时，引导 LLM 回复"请在卡片中选择"，
        # 禁止提及号源余量/预约时间/线下就医（在线问诊不依赖当天号源，
        # 避免 LLM 把 query_doctors 的 availableCount=0 误读为"号源已满"）。
        pending_doctor_choices = state.get("pending_doctor_choices")
        if pending_doctor_choices:
            names = "、".join(
                str(c.get("name") or c.get("doctor_id") or "医生") for c in pending_doctor_choices
            )
            llm_messages.append(
                {
                    "role": "system",
                    "content": (
                        f"医生列表（{names}）已生成选择卡片，用户可直接点选。"
                        "请用简短回复告知用户'已为您找到以下医生，请在卡片中选择'，"
                        "然后停止。⚠️ 不得提及号源余量、号源已满、预约时间、线下就医"
                        "或挂号——在线问诊随时可发起，不依赖医生当天号源。"
                    ),
                }
            )

        # 风险标记注入（系分 §7.1）：safety_check 记录的未完成/被拒操作，
        # 要求 LLM 如实告知用户，避免静默失败
        risk_warning = _format_risk_flags(state.get("risk_flags", []))
        if risk_warning:
            content = f"重要提示（必须在回复中如实告知用户）：{risk_warning}。语气平和，不夸大。"
            llm_messages.append({"role": "system", "content": content})

        response = await llm.ainvoke(llm_messages)
        # BaseMessage.content 可为 str 或多段内容列表（OpenAI 格式），统一转 str
        raw_content = response.content
        reply_content = raw_content if isinstance(raw_content, str) else str(raw_content)

        logger.info("回复生成成功: 长度=%d, 意图=%s", len(reply_content), intent)
        return {"messages": [{"role": "assistant", "content": reply_content}]}

    except Exception as e:
        logger.error("回复生成失败: %s", e)
        fallback_message = "抱歉，我遇到了一些问题，请稍后重试。"
        return {"messages": [{"role": "assistant", "content": fallback_message}]}


def _format_risk_flags(risk_flags: list[str]) -> str | None:
    """风险标记转中文警告（系分 §7.1：safety_check 追加，reply 注入回复）。

    支持 safety_check 写入的两种前缀：
    - ``redis_unavailable_{tool}``：L2 工具因 Redis 不可用被拒绝执行
    - ``blocked_{tool}``：L3/L4 未授权工具被拦截

    Args:
        risk_flags: AgentState.risk_flags（safety_check 追加的风险标记列表）。

    Returns:
        str | None: 中文警告文本；无风险标记时返回 None。
    """
    warnings: list[str] = []
    for flag in risk_flags:
        if flag.startswith("redis_unavailable_"):
            tool = flag.removeprefix("redis_unavailable_")
            warnings.append(f"「{tool}」操作因系统暂时不可用未能执行")
        elif flag.startswith("blocked_"):
            tool = flag.removeprefix("blocked_")
            warnings.append(f"「{tool}」操作未获授权，已拦截")
    if not warnings:
        return None
    return "；".join(warnings)


def _format_tool_results(tool_results: list[dict[str, Any]]) -> str:
    """格式化工具调用结果为 LLM 可读的自然语言文本。

    从 Java 响应信封（{code, message, data, traceId}）提取内层业务数据，
    JSON 序列化后注入 LLM 上下文，确保 LLM 基于真实数据生成回复。

    Args:
        tool_results: 工具执行结果列表。

    Returns:
        str: 含真实业务数据的文本摘要。
    """
    summaries = []
    # 截断阈值入 settings（P3-5），避免硬编码；循环外取一次避免重复读配置
    max_chars = get_settings().max_data_chars
    for result in tool_results:
        tool_name = result.get("tool_name", "未知工具")
        success = result.get("success", False)

        if success:
            data = result.get("data", {})
            # 尝试解 Java 响应信封（{code, message, data, traceId}），提取内层业务数据
            if isinstance(data, dict) and "data" in data and data.get("data") is not None:
                payload = data["data"]
            else:
                payload = data  # 本地工具或非信封格式
            try:
                text = json.dumps(payload, ensure_ascii=False)
            except (TypeError, ValueError):
                text = str(payload)
            if len(text) > max_chars:
                text = text[:max_chars] + "...(截断)"
            summaries.append(f"✓ {tool_name}: {text}")
        else:
            error_msg = result.get("error", {}).get("message", "未知错误")
            summaries.append(f"✗ {tool_name}: 执行失败 - {error_msg}")

    return "\n".join(summaries)
