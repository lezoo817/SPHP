"""挂号子图（系分 §5.2.1）。

调 query_departments / query_schedule_slots / create_appointment（含 L2 确认）。
绑定挂号场景工具白名单：科室/医生/号源查询 + 挂号创建/取消/候补 + 支付查询。
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


def build_registration_graph() -> Any:
    """构造挂号子图（编译后）。"""
    return build_tool_subgraph(tool_names=REGISTRATION_TOOLS)
