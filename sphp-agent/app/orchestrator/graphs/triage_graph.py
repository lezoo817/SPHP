"""导诊子图（系分 §5.2.1）。

追问症状 -> RAG 检索 -> 推荐科室 -> 调 query_doctors 查医生。
骨架与其它业务子图一致，差异在注入的工具集（待子图分化时覆盖）。
"""

from app.orchestrator.graphs._common import build_tool_subgraph


def build_triage_graph():
    """构造导诊子图（编译后）。"""
    return build_tool_subgraph()
