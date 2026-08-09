"""用户情绪识别（方向 A 情感陪伴）。

从用户最新消息中识别情绪标签，供 reply_node 在回复时注入安抚/致歉等
确定性引导。采用关键词匹配（确定性、零额外 LLM 调用），不改变意图分类链路。
"""

from typing import Literal

# 情绪标签：neutral 中性 / anxious 焦虑担忧 / distressed 低落难过 /
# angry 不满愤怒 / positive 积极。
Emotion = Literal["neutral", "anxious", "distressed", "positive", "angry"]

# 情绪关键词表。按检测优先级排序：医疗焦虑最优先（牵涉安全边界），
# 其次低落/不满，积极最末——同一句话含多类情绪词时优先安抚/致歉。
EMOTION_RULES: list[tuple[Emotion, tuple[str, ...]]] = [
    (
        "anxious",
        (
            "焦虑",
            "害怕",
            "担心",
            "恐惧",
            "恐慌",
            "紧张",
            "是不是得了",
            "是不是得",
            "重病",
            "癌症",
            "肿瘤",
            "绝症",
            "治不好",
            "睡不着",
            "失眠",
            "会死",
            "吓死",
        ),
    ),
    (
        "distressed",
        (
            "难过",
            "伤心",
            "心情不好",
            "抑郁",
            "崩溃",
            "哭",
            "压力大",
            "想不开",
            "无助",
            "绝望",
        ),
    ),
    (
        "angry",
        ("投诉", "差评", "什么破", "垃圾", "气死", "敷衍"),
    ),
    (
        "positive",
        ("谢谢", "感谢", "太好了", "开心", "帮大忙", "非常满意", "满意"),
    ),
]


def detect_emotion(message: str) -> Emotion:
    """识别用户消息中的情绪标签。

    按 EMOTION_RULES 的优先级顺序匹配关键词，命中即返回对应情绪。

    Args:
        message: 用户最新一条消息文本。

    Returns:
        Emotion: 命中的情绪标签；无命中或空文本时返回 "neutral"。
    """
    if not message:
        return "neutral"
    for emotion, keywords in EMOTION_RULES:
        if any(keyword in message for keyword in keywords):
            return emotion
    return "neutral"
