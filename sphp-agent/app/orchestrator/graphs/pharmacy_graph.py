"""购药子图（系分 §5.2.1）。

调 query_pharmacy_stock -> create_drug_order（含 L2 确认）。
骨架与其它业务子图一致。
"""

from app.orchestrator.graphs._common import build_tool_subgraph


def build_pharmacy_graph():
    """构造购药子图（编译后）。"""
    return build_tool_subgraph()
