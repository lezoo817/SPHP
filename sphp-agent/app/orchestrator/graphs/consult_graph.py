"""问诊子图（系分 §5.2.1）。

调 query_consultations / query_prescriptions -> interpret_prescription。
绑定问诊场景工具白名单：预问诊/问诊消息 + 处方查询解读。
"""

from app.orchestrator.graphs._common import build_tool_subgraph

# 问诊场景工具白名单（系分 §5.2.1 问诊链路）
CONSULTATION_TOOLS = [
    "save_pre_consultation",  # 提交预问诊（L2）
    "query_consultations",  # 问诊记录查询
    "send_consultation_message",  # 发送问诊消息（L2）
    "query_prescriptions",  # 处方查询
    "interpret_prescription",  # 处方解读
]


def build_consultation_graph():
    """构造问诊子图（编译后）。"""
    return build_tool_subgraph(tool_names=CONSULTATION_TOOLS)
