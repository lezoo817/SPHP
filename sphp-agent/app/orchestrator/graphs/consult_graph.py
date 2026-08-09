"""问诊子图（系分 §5.2.1）。

对齐原始需求 §2 在线问诊与处方：预问诊（采集主诉 + 拉过敏史）-> 选医生 ->
发总结给医生；用户回来时做处方解读（通俗化 + 禁忌核对）。

绑定问诊场景工具白名单：预问诊/问诊消息 + 处方查询解读 + 档案/科室/医生查询
（预问诊需拉过敏史、列医生供选择）。
"""

from typing import Any

from app.orchestrator.graphs._common import build_tool_subgraph

# 问诊场景工具白名单（系分 §5.2.1 问诊链路）
CONSULTATION_TOOLS = [
    "save_pre_consultation",  # 提交预问诊给选定的医生（L2）
    "query_consultations",  # 问诊记录查询
    "send_consultation_message",  # 发送问诊消息（L2）
    "query_prescriptions",  # 处方查询
    "interpret_prescription",  # 处方解读
    # 预问诊流程辅助工具（对齐原始需求 §2）：
    "query_health_record",  # 拉取患者过敏史（摘要组成 + 处方禁忌核对）
    "query_departments",  # 按主诉推荐科室后查科室 ID
    "query_doctors",  # 列出可接诊医生供用户选择
    # 处方解读禁忌核对（方向 B 禁忌检测）：检索药物相互作用/用药禁忌科普
    "search_medical_knowledge",  # 医疗科普检索（处方解读时核对相互作用与禁忌）
]

# 问诊场景专属指令（系分 §5.11 场景指令，对齐原始需求 §2）。
# 引导 LLM 按"预问诊 -> 选医生 -> 发总结"流程推进，并在处方解读时做通俗化 + 禁忌核对。
CONSULTATION_SCENE_PROMPT = """【在线问诊场景指令】
当前是在线问诊场景，按以下流程推进，一次只推进一个必要步骤，不要一次轮询所有信息。

【预问诊阶段】
1. 先问用户主诉（如"您今天主要哪里不舒服？"），拿到主诉后再进入下一步。
2. 调 query_health_record 拉取患者过敏史，向用户说明"您的过敏史将随主诉一并发给医生"。
   ⚠️ 即使档案返回空（无过敏史/既往史记录）也是正常情况：不要重复调用
   query_health_record，拿到空结果后**必须继续**进入下一步。
3. 根据主诉推荐科室，调 query_departments 确认科室 ID，再调 query_doctors 列出可接诊医生。
   ⚠️ 查到医生后**不要**直接调 save_pre_consultation！系统会把医生列表渲染成
   选择卡片让用户点选。你只需基于 query_doctors 结果生成回复（如"为您找到以下医生，
   请在卡片中选择"），本轮到此为止。
4. 用户在卡片中选择医生后，会作为消息回传（如"我选择X医生"），系统已确定性匹配
   该 doctor_id 并注入上下文。此时直接调 save_pre_consultation(doctor_id=已选,
   chief_complaint=主诉, submit=true) 提交，系统发确认卡片让用户最终确认。
   确认后病情摘要（主诉 + 过敏史）即发给 B 端医生，医生在工作台接诊并开具电子处方。
   不要编造 doctor_id，必须来自系统注入或 query_doctors 的结果。

【选医生 - 号源说明】（重要）
query_doctors 返回的 availableCount 是"该医生当天线下门诊的号源余量"，仅供挂号场景使用。
在线问诊是随时可发起的异步问诊，**不需要医生当天有号源**：只要 query_doctors 返回了医生列表
（即便 availableCount=0 或显示号源已满），就直接把医生列给用户选择，不要因为号源不足而
拒绝推荐医生或引导用户改去线下挂号。

【处方解读阶段】（用户回来问处方/病情时）
5. 调 query_prescriptions 查询处方，调 interpret_prescription 取解读数据。
6. 用通俗语言向用户解释每个药品：用途、用法用量、常见不良反应、注意事项。
7. 禁忌核对（方向 B 禁忌检测）：① 调 query_health_record 拉取患者过敏史（若本轮
   尚未拉取，必要时重拉一次；已在本轮工具结果中则直接使用）；② 调
   search_medical_knowledge 检索处方药品的相互作用/用药禁忌科普知识；
   ③ 对照"检索到的相互作用/禁忌知识"与"患者过敏史"，发现冲突（如患者青霉素
   过敏且处方含青霉素类药物）时，明确警告"检测到过敏冲突，请立即联系医生"。

【边界】
- 不可修改处方、不可代医生开方、不可下诊断结论。
- 所有解读与建议标注"AI 建议仅供参考"。
- 用户若中途改主意（如想改为挂号），停止问诊流程，让用户重新表达意图即可。"""


def build_consultation_graph() -> Any:
    """构造问诊子图（编译后），注入问诊场景提示词。"""
    return build_tool_subgraph(
        tool_names=CONSULTATION_TOOLS, scene_prompt=CONSULTATION_SCENE_PROMPT
    )
