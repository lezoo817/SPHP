"""导诊子图（系分 §5.2.1）。

追问症状 -> RAG 检索 -> 推荐科室 -> 调 query_doctors 查医生。
绑定导诊场景工具白名单：导诊评估 + 科普检索 + 科室/医生查询 + 健康档案参考。
"""

from app.orchestrator.graphs._common import build_tool_subgraph

# 导诊场景工具白名单（系分 §5.2.1 导诊链路）
TRIAGE_TOOLS = [
    "create_triage_assessment",  # 症状导诊评估
    "search_medical_knowledge",  # 医疗科普检索
    "query_departments",  # 科室查询（推荐科室辅助）
    "query_doctors",  # 医生查询（推荐医生）
    "query_health_record",  # 健康档案（导诊参考）
]


def build_triage_graph():
    """构造导诊子图（编译后）。"""
    return build_tool_subgraph(tool_names=TRIAGE_TOOLS)
