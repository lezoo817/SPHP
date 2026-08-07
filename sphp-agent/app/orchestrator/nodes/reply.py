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

# 处方解读来源标识，与 MCP 处方工具返回的数据契约一致。
_OFFICIAL_READY_SOURCE = "OFFICIAL_READY"
_AI_FALLBACK_SOURCE = "AI_FALLBACK"


def _extract_degraded_health_record(
    tool_results: list[dict[str, Any]] | None,
) -> bool:
    """识别健康档案是否发生了来源降级（2026-08-07）。

    前端会话残留他账号就诊人 ID 时，``query_health_record`` 被 Java 数据隔离
    拒绝（403 / A0301）后确定性降级为查当前账号本人档案，并在成功结果 data
    中附 ``agent_degraded: True`` 来源标记（见 health.py 该函数）。本函数扫描
    本轮工具结果，命中该标记即返回 True，供 reply_node 注入降级引导。

    Args:
        tool_results: 本轮工具执行结果列表。

    Returns:
        bool: 存在降级成功的健康档案来源标记时返回 True。
    """
    for result in tool_results or []:
        if result.get("tool_name") != "query_health_record" or not result.get("success"):
            continue
        data = result.get("data")
        if not isinstance(data, dict):
            continue
        payload = data.get("data")
        if isinstance(payload, dict) and payload.get("agent_degraded"):
            return True
    return False


def _extract_prescription_interpretation(
    tool_results: list[dict[str, Any]] | None,
) -> dict[str, Any] | None:
    """提取本轮处方解读工具的标准化结果。

    Args:
        tool_results: 本轮工具执行结果列表。

    Returns:
        含 source 的解读载荷；本轮没有成功处方解读时返回 None。
    """
    for result in tool_results or []:
        if result.get("tool_name") != "interpret_prescription" or not result.get("success"):
            continue
        response = result.get("data")
        if not isinstance(response, dict):
            continue
        payload = response.get("data")
        if isinstance(payload, dict) and payload.get("source") in (
            _OFFICIAL_READY_SOURCE,
            _AI_FALLBACK_SOURCE,
        ):
            return payload
    return None


def _format_official_interpretation(payload: dict[str, Any]) -> str | None:
    """组合正式处方解读原文及其免责声明。

    Args:
        payload: source 为 OFFICIAL_READY 的标准化解读载荷。

    Returns:
        未改写的正式解读正文；内容缺失时返回 None。
    """
    interpretation = payload.get("interpretation")
    if not isinstance(interpretation, dict):
        return None
    content = interpretation.get("content")
    if not isinstance(content, str) or not content.strip():
        return None
    sections = [content.strip()]
    disclaimer = interpretation.get("disclaimer")
    if isinstance(disclaimer, str) and disclaimer.strip() and disclaimer.strip() not in content:
        sections.append(disclaimer.strip())
    return "\n\n".join(sections)


def _build_ai_fallback_context(payload: dict[str, Any]) -> str | None:
    """构造 AI 即时处方解读的严格回复约束。

    Args:
        payload: source 为 AI_FALLBACK 的标准化解读载荷。

    Returns:
        注入回复模型的系统上下文；处方详情缺失时返回 None。
    """
    prescription = payload.get("prescription")
    if not isinstance(prescription, dict):
        return None
    try:
        detail = json.dumps(prescription, ensure_ascii=False)
    except (TypeError, ValueError):
        return None
    return (
        "当前回复必须基于以下真实处方详情生成即时说明：\n"
        f"{detail}\n\n"
        "这是“AI 即时解读（未经过医生审核）”，首行必须使用该标题。"
        "输出必须是自然的纯文本段落，不得使用 Markdown 标题、列表、编号、星号、"
        "反引号或其他标记语法。"
        "必须使用换行分段：标题后空一行；每种药品独立成段，药品段之间空一行；"
        "药品名称，常见副作用，用药提醒等分点段落间,也要空一行；"
        "最后的总体提醒另起一段并与药品段空一行。"
        "每种药品段内按“药品名称：”“主要用途：”“常见副作用：”“用药提醒：”分行输出，"
        "每个字段各占一行，字段之间不使用项目符号。"
        "对处方中的每种药品，使用通俗语言说明其常见用途、可能帮助缓解的常见症状、"
        "常见副作用和关键用药注意事项；用途和副作用使用“通常”“可能”等表述，"
        "不能据此判断用户患有对应疾病。"
        "可结合药品通用知识提示孕哺期、严重肝肾功能异常、过敏史、驾车和检查前停药等注意事项，"
        "但不编造具体停药时长、禁忌或相互作用；不确定时应建议咨询医生或药师。"
        "处方中的规格、单次用量、频次、用法和疗程必须按原始数据复述；任一字段缺失时必须明确写“处方未提供该信息”。"
        "不得调整剂量、频次、疗程，也不得要求用户自行停药或换药。"
        "末尾必须写“本说明仅供健康信息理解，请遵医嘱用药；如有不适请及时联系医生。”"
    )


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
        interpretation = _extract_prescription_interpretation(state.get("tool_results"))
        if interpretation and interpretation.get("source") == _OFFICIAL_READY_SOURCE:
            official_content = _format_official_interpretation(interpretation)
            if official_content:
                # 正式解读由医生或既有生产链路确认，必须原样展示，不能交给 LLM 改写。
                return {"messages": [{"role": "assistant", "content": official_content}]}

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

        # 档案来源降级提示（2026-08-07）：前端会话残留了他账号的就诊人 ID 时，
        # query_health_record 被 Java 数据隔离拒绝后降级为查当前账号本人档案
        # （见 health.py query_health_record）。此处识别该来源标记，引导 LLM
        # 如实说明档案来源、提示用户重新选择就诊人，且禁忌核对不得把本人档案
        # 当成目标就诊人的档案，避免基于错误的过敏史做医疗判断。
        degraded = _extract_degraded_health_record(tool_results)
        if degraded:
            llm_messages.append(
                {
                    "role": "system",
                    "content": (
                        "当前读取的健康档案为**当前登录账号本人**的档案（前端选中的就诊人"
                        "无权访问，系统已自动降级读取本人档案）。"
                        "请如实告知用户：健康档案未能按所选就诊人读取，已改用本人档案；"
                        "如需按指定就诊人查看，请返回首页重新选择就诊人。"
                        "⚠️ 禁忌核对等医疗判断必须基于本人档案，不得声称已核对目标就诊人的过敏史。"
                    ),
                }
            )

        if interpretation and interpretation.get("source") == _AI_FALLBACK_SOURCE:
            fallback_context = _build_ai_fallback_context(interpretation)
            if fallback_context:
                # 即时说明只能使用已授权读取的处方详情，避免模型补造医疗事实。
                llm_messages.append({"role": "system", "content": fallback_context})

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
