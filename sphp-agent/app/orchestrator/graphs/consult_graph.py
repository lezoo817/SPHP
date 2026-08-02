"""问诊子图（系分 §5.2.1）。

调 query_consultations / query_prescriptions -> interpret_prescription。
骨架与其它业务子图一致。
"""

from app.orchestrator.graphs._common import build_tool_subgraph


def build_consultation_graph():
    """构造问诊子图（编译后）。"""
    return build_tool_subgraph()
