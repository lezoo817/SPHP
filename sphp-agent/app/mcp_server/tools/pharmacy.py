"""购药工具封装（系分 §5.3）。

MCP 工具：query_pharmacy_stock, create_drug_order, query_drug_orders,
          cancel_drug_order, confirm_drug_receipt
对应 Java API: /api/c/v1/pharmacies/inventory, /drug-orders 等
"""

from app.infrastructure.java_client import call_java_api


async def query_pharmacy_stock(prescription_id: int, patient_id: int | None = None, user_id: int | None = None) -> dict:
    """查询附近药店库存与价格。"""
    params = {"prescription_id": prescription_id}
    if patient_id:
        params["patient_id"] = patient_id
    return await call_java_api("GET", "/api/c/v1/pharmacies/inventory", params=params, user_id=user_id, scope="c_end")


async def create_drug_order(prescription_id: int, pharmacy_id: int, delivery_address: str, patient_id: int | None = None, user_id: int | None = None) -> dict:
    """创建购药订单草稿（待付款状态）。"""
    body = {"prescription_id": prescription_id, "pharmacy_id": pharmacy_id, "delivery_address": delivery_address}
    if patient_id:
        body["patient_id"] = patient_id
    return await call_java_api("POST", "/api/c/v1/drug-orders", body=body, user_id=user_id, scope="c_end")


async def query_drug_orders(drug_order_id: int | None = None, status: str | None = None, logistics_status: str | None = None, user_id: int | None = None) -> dict:
    """查询购药订单列表或详情。"""
    if drug_order_id:
        return await call_java_api("GET", f"/api/c/v1/drug-orders/{drug_order_id}", user_id=user_id, scope="c_end")
    params = {}
    if status:
        params["status"] = status
    if logistics_status:
        params["logistics_status"] = logistics_status
    return await call_java_api("GET", "/api/c/v1/drug-orders", params=params, user_id=user_id, scope="c_end")


async def cancel_drug_order(drug_order_id: int, user_id: int | None = None) -> dict:
    """取消未支付购药订单。"""
    return await call_java_api("POST", f"/api/c/v1/drug-orders/{drug_order_id}/cancel", user_id=user_id, scope="c_end")


async def confirm_drug_receipt(drug_order_id: int, user_id: int | None = None) -> dict:
    """确认购药收货。"""
    return await call_java_api("POST", f"/api/c/v1/drug-orders/{drug_order_id}/confirm-receipt", user_id=user_id, scope="c_end")


def register(server):
    """注册工具到 MCP Server。"""
    pass
