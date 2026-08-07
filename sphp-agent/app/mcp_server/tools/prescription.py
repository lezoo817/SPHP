"""处方工具封装（系分 §5.3）。

MCP 工具：query_prescriptions, interpret_prescription
对应 Java API: /api/c/v1/prescriptions 等
接口路径统一由 java_api_map 契约表解析。
"""

from typing import Any

from app.infrastructure.java_client import call_java_api

# 解读来源：医生确认的正式内容或仅本轮生成的 AI 即时说明。
OFFICIAL_READY_SOURCE = "OFFICIAL_READY"
AI_FALLBACK_SOURCE = "AI_FALLBACK"


def _is_interpretation_not_ready(result: dict[str, Any]) -> bool:
    """判断正式解读是否因未生成而不可读取。

    Args:
        result: Java 调用结果，可能是 HTTP 200 业务错误信封或 HTTP 错误包装。

    Returns:
        仅业务码为 B0202 时返回 True。
    """
    if result.get("code") == "B0202":
        return True
    error = result.get("error")
    return isinstance(error, dict) and error.get("code") == "B0202"


def _is_success_envelope(result: dict[str, Any]) -> bool:
    """判断 Java 统一响应是否为成功信封。

    Args:
        result: Java REST API 返回结果。

    Returns:
        成功码为 00000 时返回 True。
    """
    return result.get("code") == "00000"


def _wrap_official_interpretation(result: dict[str, Any]) -> dict[str, Any]:
    """为正式解读补充来源标识。

    Args:
        result: Java 正式解读成功响应。

    Returns:
        保留原响应字段并标记为正式解读的成功信封。
    """
    return {
        **result,
        "data": {
            "source": OFFICIAL_READY_SOURCE,
            "interpretation": result.get("data"),
        },
    }


def _wrap_ai_fallback(detail: dict[str, Any]) -> dict[str, Any]:
    """将处方详情转换为即时解读所需的成功信封。

    Args:
        detail: Java 处方详情成功响应。

    Returns:
        标记为 AI 即时解读来源的统一成功信封。
    """
    return {
        "code": "00000",
        "message": "已获取处方详情，正在生成AI即时解读",
        "data": {
            "source": AI_FALLBACK_SOURCE,
            "prescription": detail.get("data"),
        },
        "traceId": detail.get("traceId", ""),
    }


async def query_prescriptions(
    prescription_id: int | None = None,
    patient_id: int | None = None,
    recent_days: int | None = None,
    user_id: int | None = None,
) -> dict[str, Any]:
    """查询处方列表或详情。

    Args:
        prescription_id: 处方 ID，传入时查询详情。
        patient_id: 可选就诊人 ID，列表查询时由 Java 校验归属。
        recent_days: 可选最近天数，仅受控记录选择入口传入 30。
        user_id: 当前用户 ID。

    Returns:
        Java 处方列表或详情响应。
    """
    if prescription_id is not None:
        return await call_java_api(
            api_name="query_prescriptions:detail",
            path_params={"prescription_id": prescription_id},
            user_id=user_id,
        )
    params: dict[str, Any] = {}
    if patient_id is not None:
        params["patient_id"] = patient_id
    if recent_days is not None:
        params["recent_days"] = recent_days
    return await call_java_api(api_name="query_prescriptions:list", params=params, user_id=user_id)


async def interpret_prescription(
    prescription_id: int, user_id: int | None = None
) -> dict[str, Any]:
    """优先读取正式解读，未生成时基于处方详情生成即时说明。

    Args:
        prescription_id: 处方 ID。
        user_id: 当前用户 ID，由 MCP 分发器注入用于数据隔离。

    Returns:
        正式解读或含处方明细的 AI 即时解读输入信封。
    """
    official = await call_java_api(
        api_name="interpret_prescription",
        path_params={"prescription_id": prescription_id},
        user_id=user_id,
    )
    # 已有 READY 正式解读时保留原文，后续回复节点不会让 LLM 改写。
    if _is_success_envelope(official):
        return _wrap_official_interpretation(official)
    # 只有“解读尚未生成”才允许即时兜底，权限和服务故障必须原样透传。
    if not _is_interpretation_not_ready(official):
        return official

    # 复用受权限保护的处方详情查询，仅读取当前用户可访问的已批准处方。
    detail = await query_prescriptions(prescription_id=prescription_id, user_id=user_id)
    if not _is_success_envelope(detail):
        return detail
    return _wrap_ai_fallback(detail)
