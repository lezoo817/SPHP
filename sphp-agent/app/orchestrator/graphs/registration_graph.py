"""挂号子图（系分 §5.2.1）。

调 query_departments / query_schedule_slots / create_appointment（含 L2 确认）。
绑定挂号场景工具白名单：科室/医生/号源查询 + 挂号创建/取消/候补 + 支付查询。

⚠️ 挂号与在线问诊严格区分（2026-08-07 修复）：挂号是"选医生 -> 选时段 ->
创建挂号订单 -> 引导支付"的流程，**不采集主诉、不发送预问诊**。选医生发卡
逻辑（tool_caller 的 M8-6/7/8）仅问诊子图生效，挂号子图不受影响；本场景指令
进一步明确 LLM：挂号不向用户索要症状/主诉，创建订单后引导去支付。
"""

from typing import Any

from app.orchestrator.graphs._common import build_tool_subgraph

# 挂号场景工具白名单（系分 §5.2.1 挂号链路）
REGISTRATION_TOOLS = [
    "query_departments",  # 科室查询
    "query_doctors",  # 医生查询
    "query_schedule_slots",  # 号源时段查询
    "query_appointments",  # 挂号订单查询
    "create_appointment",  # 创建挂号（L2）
    "cancel_appointment",  # 取消挂号（L2）
    "join_waitlist",  # 候补登记（L2）
    "query_payment_status",  # 支付状态查询
]

# 挂号场景专属指令（系分 §5.11 场景指令，2026-08-07 新增）。
# 明确挂号流程与在线问诊的边界：不采集主诉、不发送预问诊，创建订单后引导支付。
REGISTRATION_SCENE_PROMPT = """【预约挂号场景指令】
当前是预约挂号场景，按以下流程推进，一次只推进一个必要步骤，不要一次轮询所有信息。

【挂号流程】
1. 用户说"挂X科室/医生的号"或"预约挂号"时，先查科室（query_departments），
   再按科室查医生（query_doctors），再查所选医生的可预约时段（query_schedule_slots），
   最后调 create_appointment 创建挂号订单（含 hospital_id / slot_id / patient_id）。
2. 号源已满时调 join_waitlist 登记候补。
3. 查询号源（query_schedule_slots）若今天/明天均无可用时段，**如实告知用户无号源**，
   不得编造号源余量、出诊时间或挂号费；换日期/换医生查号源必须实际调用查询工具，
   拿到真实结果后再回复，不要凭空声称某日有号。
4. 创建挂号订单后系统会发确认卡片，用户确认后挂号订单锁定，**引导用户前往支付
   完成缴费，缴费成功即挂号成功**。

【边界 - 与在线问诊严格区分】
- ⚠️ 挂号**不采集主诉**！不要问"您哪里不舒服""请描述您的症状"——那是在线问诊
  （save_pre_consultation）才需要的，挂号只需要科室、医生、时段即可。
- 挂号**不发送预问诊给医生**（不调 save_pre_consultation），那是问诊流程。
- 用户想在线问诊时会明确说"在线问诊/咨询医生"，挂号场景不要主动转向问诊。
- 用户中途改主意（如想改为在线问诊），停止挂号流程，让用户重新表达即可。"""


def build_registration_graph() -> Any:
    """构造挂号子图（编译后），注入挂号场景指令。"""
    return build_tool_subgraph(
        tool_names=REGISTRATION_TOOLS, scene_prompt=REGISTRATION_SCENE_PROMPT
    )
