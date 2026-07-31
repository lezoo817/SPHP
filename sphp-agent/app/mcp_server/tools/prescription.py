"""处方工具封装（系分 §5.3）。

MCP 工具：query_prescriptions, interpret_prescription
对应 Java API: /api/c/v1/prescriptions 等
"""

from app.infrastructure.java_client import call_java_api


async def query_prescriptions(prescription_id: int | None = None, user_id: int | None = None) -> dict:
    """查询处方列表或详情。"""
    if prescription_id:
        return await call_java_api("GET", f"/api/c/v1/prescriptions/{prescription_id}", user_id=user_id, scope="c_end")
    return await call_java_api("GET", "/api/c/v1/prescriptions", user_id=user_id, scope="c_end")


async def interpret_prescription(prescription_id: int, user_id: int | None = None) -> dict:
    """以通俗语言解读处方药品。"""
    return await call_java_api("GET", f"/api/c/v1/prescriptions/{prescription_id}/interpretation", user_id=user_id, scope="c_end")


def register(server):
    """注册工具到 MCP Server。"""
    pass
