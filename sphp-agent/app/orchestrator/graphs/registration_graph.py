"""挂号子图（系分 §5.2.1）。

调 query_departments / query_doctors / create_appointment（含 L2 确认）。
骨架与其它业务子图一致。
"""

from app.orchestrator.graphs._common import build_tool_subgraph


def build_registration_graph():
    """构造挂号子图（编译后）。"""
    return build_tool_subgraph()
