"""处方工具封装（系分 §5.3）。

MCP 工具：query_prescriptions, interpret_prescription
对应 Java API: /api/c/v1/prescriptions 等
接口路径统一由 java_api_map 契约表解析。
"""

from typing import Any

from app.infrastructure.java_client import call_java_api


async def query_prescriptions(
    prescription_id: int | None = None, user_id: int | None = None
) -> dict[str, Any]:
    """查询处方列表或详情。"""
    if prescription_id:
        return await call_java_api(
            api_name="query_prescriptions:detail",
            path_params={"prescription_id": prescription_id},
            user_id=user_id,
        )
    return await call_java_api(api_name="query_prescriptions:list", user_id=user_id)


async def interpret_prescription(
    prescription_id: int, user_id: int | None = None
) -> dict[str, Any]:
    """以通俗语言解读处方药品。"""
    return await call_java_api(
        api_name="interpret_prescription",
        path_params={"prescription_id": prescription_id},
        user_id=user_id,
    )
