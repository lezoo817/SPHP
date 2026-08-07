"""购药工具封装（系分 §5.3）。

MCP 工具：query_pharmacy_stock, create_drug_order, query_drug_orders,
          cancel_drug_order, confirm_drug_receipt
对应 Java API: /api/c/v1/pharmacies/inventory, /drug-orders 等
接口路径统一由 java_api_map 契约表解析。
"""

from typing import Any

from app.infrastructure.java_client import call_java_api


async def query_pharmacy_stock(
    prescription_id: int, patient_id: int | None = None, user_id: int | None = None
) -> dict[str, Any]:
    """查询附近药店库存与价格。"""
    params = {"prescription_id": prescription_id}
    if patient_id is not None:
        params["patient_id"] = patient_id
    return await call_java_api(tool_name="query_pharmacy_stock", params=params, user_id=user_id)


async def recommend_pharmacies(
    prescription_id: int,
    address_id: int,
    patient_id: int | None = None,
    sort: str | None = None,
    user_id: int | None = None,
) -> dict[str, Any]:
    """推荐可配送院内药房（Java 服务端按价格/距离/配送时效加权排序）。

    对齐原始需求 §3 药店推荐。address_id 为用户收货地址 ID（前端注入
    state.address_id 或后续查地址列表获取）；返回 Java 加权排序后的药店列表。
    """
    params: dict[str, Any] = {"prescription_id": prescription_id, "address_id": address_id}
    if patient_id is not None:
        params["patient_id"] = patient_id
    if sort:
        params["sort"] = sort
    return await call_java_api(tool_name="recommend_pharmacies", params=params, user_id=user_id)


async def create_drug_order(
    prescription_id: int,
    pharmacy_id: int,
    address_id: int,
    patient_id: int | None = None,
    user_id: int | None = None,
) -> dict[str, Any]:
    """使用当前账号地址簿创建购药订单草稿（待付款状态）。"""
    body = {
        "prescription_id": prescription_id,
        "pharmacy_id": pharmacy_id,
        "address_id": address_id,
    }
    if patient_id is not None:
        body["patient_id"] = patient_id
    return await call_java_api(tool_name="create_drug_order", body=body, user_id=user_id)


async def query_drug_orders(
    drug_order_id: int | None = None,
    status: str | None = None,
    logistics_status: str | None = None,
    user_id: int | None = None,
) -> dict[str, Any]:
    """查询购药订单列表或详情。"""
    if drug_order_id is not None:
        return await call_java_api(
            api_name="query_drug_orders:detail",
            path_params={"drug_order_id": drug_order_id},
            user_id=user_id,
        )
    params = {}
    if status:
        params["status"] = status
    if logistics_status:
        params["logistics_status"] = logistics_status
    return await call_java_api(api_name="query_drug_orders:list", params=params, user_id=user_id)


async def cancel_drug_order(drug_order_id: int, user_id: int | None = None) -> dict[str, Any]:
    """取消未支付购药订单。"""
    return await call_java_api(
        api_name="cancel_drug_order",
        path_params={"drug_order_id": drug_order_id},
        user_id=user_id,
    )


async def confirm_drug_receipt(drug_order_id: int, user_id: int | None = None) -> dict[str, Any]:
    """确认购药收货。"""
    return await call_java_api(
        api_name="confirm_drug_receipt",
        path_params={"drug_order_id": drug_order_id},
        user_id=user_id,
    )
