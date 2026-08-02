"""意图识别节点（系分 §5.2.2）。

使用 LLM 分类（非规则匹配），通过构造分类 prompt 让 LLM 从预定义标签中选择。
"""

import logging
from typing import Any

from app.engine.llm.factory import build_llm
from app.orchestrator.state import AgentState
from app.orchestrator.utils import get_last_user_content

logger = logging.getLogger(__name__)

# 预定义意图标签（系分 §5.2.2）
INTENT_LABELS = {
    "triage": "分诊导诊：症状描述、科室推荐、医生推荐",
    "registration": "挂号预约：查询科室/医生/号源、创建/取消挂号订单",
    "consultation": "在线问诊：预问诊、消息发送、处方查询、病历解读",
    "pharmacy": "购药服务：药品查询、药店查询、创建/取消购药订单",
    "qa": "医疗咨询：疾病知识、用药指导、检查报告解读（需RAG检索）",
    "chitchat": "闲聊：非医疗话题、打招呼、确认回复",
}

# 意图分类系统提示词
INTENT_CLASSIFIER_PROMPT = """你是一个医疗场景的意图识别助手。

根据用户输入，从以下意图中选择最合适的一个：

{intent_descriptions}

规则：
1. 只返回意图标签，不要返回其他内容
2. 如果无法判断，返回"qa"
3. 优先匹配明确的服务请求（挂号/购药/问诊）
4. 症状描述优先归类为"triage"
5. 非医疗话题归类为"chitchat"

用户输入：
{user_message}

意图："""


async def intent_node(state: AgentState) -> dict[str, Any]:
    """意图识别节点（系分 §5.2.2）。

    分类用户意图，返回 {"intent": "triage"}。
    LLM 返回非预定义标签时默认归类为 qa（RAG 兜底回答）。
    消息 ≤10 字且命中关键词时跳过 LLM 调用，直接路由，减少首字延迟。

    Args:
        state: 当前图状态，包含 messages 字段。

    Returns:
        dict: 部分状态更新，包含 intent 字段。

    Raises:
        无：意图识别失败时降级为 "qa" 兜底。
    """
    # 获取用户最新消息（兼容 dict / BaseMessage）
    user_message = get_last_user_content(state)

    # 关键词快速通道（≤10 字时直接路由）
    if len(user_message) <= 10:
        if any(kw in user_message for kw in ["挂号", "预约", "号源", "排班"]):
            logger.info("关键词快速通道: registration")
            return {"intent": "registration"}
        if any(kw in user_message for kw in ["买药", "购药", "下单", "配送"]):
            logger.info("关键词快速通道: pharmacy")
            return {"intent": "pharmacy"}
        if any(kw in user_message for kw in ["问诊", "咨询", "医生"]):
            logger.info("关键词快速通道: consultation")
            return {"intent": "consultation"}
        if any(kw in user_message for kw in ["头疼", "发烧", "咳嗽", "症状"]):
            logger.info("关键词快速通道: triage")
            return {"intent": "triage"}

    # LLM 意图分类
    try:
        llm = build_llm()

        # 构造分类 prompt
        intent_descriptions = "\n".join(
            [f"{label}: {desc}" for label, desc in INTENT_LABELS.items()]
        )
        prompt = INTENT_CLASSIFIER_PROMPT.format(
            intent_descriptions=intent_descriptions, user_message=user_message
        )

        # 调用 LLM
        response = await llm.ainvoke(prompt)
        intent = response.content.strip().lower()

        # 验证意图标签
        if intent not in INTENT_LABELS:
            logger.warning("LLM返回未定义意图: %s, 降级为qa", intent)
            intent = "qa"

        logger.info("LLM意图分类: %s (消息长度: %d)", intent, len(user_message))
        return {"intent": intent}

    except Exception as e:
        logger.error("LLM意图分类失败: %s, 降级为qa", str(e))
        return {"intent": "qa"}  # 降级兜底
