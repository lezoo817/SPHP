"""购药子图（系分 §5.2.1）。

调 query_pharmacy_stock -> create_drug_order（含 L2 确认）。
绑定购药场景工具白名单：库存查询 + 购药订单下单/取消/收货。
"""

from app.orchestrator.graphs._common import build_tool_subgraph

# 购药场景工具白名单（系分 §5.2.1 购药链路）
PHARMACY_TOOLS = [
    "query_pharmacy_stock",  # 药店库存查询
    "create_drug_order",  # 创建购药订单（L2）
    "query_drug_orders",  # 购药订单查询
    "cancel_drug_order",  # 取消购药订单（L2）
    "confirm_drug_receipt",  # 确认收货（L2）
]


def build_pharmacy_graph():
    """构造购药子图（编译后）。"""
    return build_tool_subgraph(tool_names=PHARMACY_TOOLS)
