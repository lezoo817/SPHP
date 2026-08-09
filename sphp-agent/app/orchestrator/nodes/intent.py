"""意图识别节点（系分 §5.2.2）。

使用 LLM 分类（非规则匹配），通过构造分类 prompt 让 LLM 从预定义标签中选择。

意图粘性（修复问诊中补症状被重判 triage）：已处于业务会话时，intent_node 读取
state.intent 历史，用粘性 prompt 让 LLM 判断保持还是切换，禁用关键词快速通道
（避免症状词切回 triage）。LLM 返回未定义标签或异常时保持原意图（粘性兜底）。
"""

import logging
from typing import Any

from app.engine.llm.factory import build_llm
from app.orchestrator.emotion import detect_emotion
from app.orchestrator.state import AgentState
from app.orchestrator.utils import get_last_user_content

logger = logging.getLogger(__name__)

# 预定义意图标签（系分 §5.2.2）
INTENT_LABELS = {
    "triage": "分诊导诊：症状描述、科室推荐、医生推荐",
    "registration": "挂号预约：查询科室/医生/号源、创建/取消挂号订单",
    "consultation": "在线问诊：预问诊、选医生、发送病情摘要给医生、处方查询与解读",
    "pharmacy": "购药服务：药品查询、药店查询、创建/取消购药订单",
    "qa": "医疗咨询：疾病知识、用药指导、检查报告解读（需RAG检索）",
    "chitchat": "闲聊：非医疗话题、打招呼、确认回复",
    "health": (
        "健康档案：过敏史/既往史查询与更新、检查报告查询/录入、"
        "用药计划查询/更新、随访查询/确认、通知管理"
    ),
}

# 业务意图集合（粘性）：已处于这些意图时走粘性分类，保持流程不断裂。
# qa / chitchat 非粘性（终态/闲聊，下一轮可任意切换）。
STICKY_INTENTS = {"triage", "registration", "consultation", "pharmacy", "health"}

# 意图分类系统提示词（首轮/非粘性场景）
INTENT_CLASSIFIER_PROMPT = """你是一个医疗场景的意图识别助手。

根据用户输入，从以下意图中选择最合适的一个：

{intent_descriptions}

规则：
1. 只返回意图标签，不要返回其他内容
2. 如果无法判断，返回"qa"
3. 优先匹配明确的服务请求（挂号/购药/问诊/健康档案）
4. 症状描述优先归类为"triage"
5. 非医疗话题归类为"chitchat"

用户输入：
{user_message}

意图："""

# 粘性意图分类提示词（已处于业务会话时使用）
INTENT_STICKY_PROMPT = """你是一个医疗场景的意图识别助手。

当前会话已处于业务流程中，已识别意图：{last_intent}
根据用户最新输入，判断是「继续当前业务」还是「切换到其他业务」：

{intent_descriptions}

规则：
1. 只返回意图标签，不要返回其他内容
2. 用户补充当前业务信息时（如问诊补症状、挂号问号源、分诊描述症状），保持 {last_intent}
3. 仅当用户明确请求其他业务（如问诊中说"我想挂号"/"买药"）时才切换到对应意图
4. 症状描述（头疼/发烧等）在问诊/分诊进行中时保持当前意图，不切回 triage
5. 无法判断时保持当前意图 {last_intent}

用户输入：
{user_message}

意图："""


def _build_intent_descriptions() -> str:
    """构造意图标签描述文本，供 prompt 注入。"""
    return "\n".join(f"{label}: {desc}" for label, desc in INTENT_LABELS.items())


def _keyword_quick_channel(message: str) -> str | None:
    """关键词快速通道（≤10 字直接路由，省 LLM）。

    命中返回意图标签，未命中返回 None。仅首轮/非粘性场景使用；粘性场景由
    intent_node 直接走 LLM，不经此通道（避免症状词切回 triage）。
    """
    if len(message) > 10:
        return None
    channels: list[tuple[str, list[str]]] = [
        ("registration", ["挂号", "预约", "号源", "排班"]),
        ("pharmacy", ["买药", "购药", "下单", "配送"]),
        ("consultation", ["问诊", "咨询", "医生", "复诊"]),
        ("triage", ["头疼", "发烧", "咳嗽", "症状"]),
        ("health", ["过敏史", "检查报告", "用药计划", "随访", "通知"]),
    ]
    for intent_name, keywords in channels:
        if any(kw in message for kw in keywords):
            logger.info("关键词快速通道: %s", intent_name)
            return intent_name
    return None


async def _llm_classify(prompt: str) -> str:
    """LLM 意图分类，返回清洗后的意图标签（未经验证，由调用方兜底）。"""
    llm = build_llm()
    response = await llm.ainvoke(prompt)
    # content 可为 str 或多段列表，统一转 str 再清洗
    raw = response.content
    return raw.strip().lower() if isinstance(raw, str) else str(raw).strip().lower()


async def intent_node(state: AgentState) -> dict[str, Any]:
    """意图识别节点（系分 §5.2.2，含意图粘性）。

    分类用户意图并识别情绪，返回 ``{"intent": "triage", "emotion": "anxious"}``。

    意图粘性：已处于业务会话（``state.intent`` ∈ STICKY_INTENTS）时，用粘性 prompt
    让 LLM 判断保持/切换，禁用关键词快速通道（避免问诊中补症状被切回 triage）。
    LLM 返回未定义标签或异常时保持原意图（粘性兜底）。

    首轮或非粘性（None / qa / chitchat）：关键词快速通道（≤10 字）+ LLM 分类，
    失败降级为 ``"qa"`` 兜底。

    情绪识别（方向 A 情感陪伴）：从最新用户消息用关键词确定性判定情绪
    （detect_emotion），所有返回路径均带上 emotion，供 reply_node 注入安抚引导。
    意图分类逻辑本身不变，情绪识别不额外调用 LLM。

    Args:
        state: 当前图状态，包含 messages 与 intent（历史意图）字段。

    Returns:
        dict: 部分状态更新，包含 intent 与 emotion 字段。

    Raises:
        无：意图识别失败时降级兜底（粘性场景保持原意图，非粘性降级 qa）。
    """
    user_message = get_last_user_content(state)
    last_intent = state.get("intent")
    # 情绪识别（确定性关键词，所有分支共用）
    emotion = detect_emotion(user_message)

    # 意图粘性：已处于业务会话 -> LLM 判断保持/切换（禁用关键词快速通道）
    if last_intent in STICKY_INTENTS:
        prompt = INTENT_STICKY_PROMPT.format(
            intent_descriptions=_build_intent_descriptions(),
            last_intent=last_intent,
            user_message=user_message,
        )
        try:
            intent = await _llm_classify(prompt)
            if intent not in INTENT_LABELS:
                logger.warning("LLM粘性返回未定义意图: %s, 保持 %s", intent, last_intent)
                intent = last_intent
            logger.info(
                "LLM粘性意图分类: %s (上一意图: %s, 消息长度: %d)",
                intent,
                last_intent,
                len(user_message),
            )
            return {"intent": intent, "emotion": emotion}
        except Exception as e:
            logger.error("LLM粘性意图分类失败: %s, 保持 %s", str(e), last_intent)
            return {"intent": last_intent, "emotion": emotion}

    # 首轮/非粘性：关键词快速通道优先
    quick = _keyword_quick_channel(user_message)
    if quick:
        return {"intent": quick, "emotion": emotion}

    # LLM 意图分类
    try:
        prompt = INTENT_CLASSIFIER_PROMPT.format(
            intent_descriptions=_build_intent_descriptions(),
            user_message=user_message,
        )
        intent = await _llm_classify(prompt)
        if intent not in INTENT_LABELS:
            logger.warning("LLM返回未定义意图: %s, 降级为qa", intent)
            intent = "qa"
        logger.info("LLM意图分类: %s (消息长度: %d)", intent, len(user_message))
        return {"intent": intent, "emotion": emotion}

    except Exception as e:
        logger.error("LLM意图分类失败: %s, 降级为qa", str(e))
        return {"intent": "qa", "emotion": emotion}  # 降级兜底
