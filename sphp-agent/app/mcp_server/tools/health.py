"""健康管理工具封装（系分 §5.3）。

MCP 工具：query_health_record, manage_allergy, manage_medical_history,
          query_reports, create_report, query_medication_plans,
          update_medication_plan, query_follow_ups, confirm_follow_up
对应 Java API: /api/c/v1/health-record, /reports, /medication-plans, /follow-ups 等
接口路径统一由 java_api_map 契约表解析。
"""

import asyncio
import logging
from typing import Any

from app.infrastructure.java_client import call_java_api

logger = logging.getLogger(__name__)


def _is_patient_access_denied(result: dict[str, Any]) -> bool:
    """判断健康档案请求是否被数据隔离拒绝（无法访问指定就诊人）。

    前端 ``context.patient_id`` 来自 sessionStorage ``sphp_c_selection``，登出/
    切换账号时不随登录态清空，可能残留**其他账号**的就诊人 ID。此时 Java 按
    ``patient_user_relation`` 归属校验拒绝（403 / 业务码 A0301「无权访问该
    就诊人健康档案」）。本函数识别该失败，供 :func:`query_health_record` 降级。

    Args:
        result: call_java_api 返回的失败信封或 Java 错误信封。

    Returns:
        bool: HTTP 403 或业务码为 A0301（访问未授权）时返回 True。
    """
    if result.get("code") == "A0301":
        return True
    error = result.get("error")
    return (
        isinstance(error, dict)
        and (error.get("code") == "A0301" or error.get("http_status") == 403)
    )


async def query_health_record(
    patient_id: int | None = None, user_id: int | None = None
) -> dict[str, Any]:
    """查询健康档案（含过敏史、既往史）。

    权限降级（2026-08-07）：携带 ``patient_id`` 查询被 Java 数据隔离拒绝
    （403 / A0301——前端会话残留了他账号的就诊人 ID）时，**确定性降级**为
    不传 patient_id 重试（Java 侧解析为当前账号 SELF 本人档案），避免一次
    误选直接打爆处方解读/预问诊流程。降级成功的结果附 ``agent_degraded``
    标记，供回复节点识别来源并引导用户重新选择就诊人；降级也失败则原样
    返回（不掩盖权限错误）。

    Args:
        patient_id: 就诊人 ID（选填，不传查当前账号本人档案）。
        user_id: 当前用户 ID（MCP 分发器注入，供 Java 数据隔离）。

    Returns:
        dict: Java 信封；降级成功时 data 内附 ``agent_degraded: True`` 来源
            标记（标记仅本轮有效，Java 回包不含该字段）。
    """
    if patient_id is None:
        return await call_java_api(tool_name="query_health_record", user_id=user_id)
    result = await call_java_api(
        tool_name="query_health_record", params={"patient_id": patient_id}, user_id=user_id
    )
    # 仅权限拒绝降级：其他失败（就诊人不存在/服务异常）不降级，原样透传
    if not _is_patient_access_denied(result):
        return result
    logger.info(
        "健康档案访问被拒（patient_id=%s），降级查询当前账号本人档案",
        patient_id,
    )
    fallback = await call_java_api(tool_name="query_health_record", user_id=user_id)
    if fallback.get("code") != "00000":
        # 降级也失败：返回原始权限错误（含 http_status），不掩盖
        return result
    return {
        **fallback,
        "data": {**(fallback.get("data") or {}), "agent_degraded": True},
    }


async def manage_allergy(
    allergen: str,
    allergy_id: int | None = None,
    reaction: str | None = None,
    user_id: int | None = None,
) -> dict[str, Any]:
    """管理过敏史记录（不带 allergy_id 新增，带 allergy_id 修改）。"""
    body = {"allergen": allergen}
    if reaction:
        body["reaction"] = reaction
    if allergy_id is not None:
        return await call_java_api(
            api_name="manage_allergy:update",
            path_params={"allergy_id": allergy_id},
            body=body,
            user_id=user_id,
        )
    return await call_java_api(api_name="manage_allergy:create", body=body, user_id=user_id)


async def manage_medical_history(
    content: str,
    history_id: int | None = None,
    occurred_at: str | None = None,
    user_id: int | None = None,
) -> dict[str, Any]:
    """管理既往史记录（不带 history_id 新增，带 history_id 修改）。"""
    body = {"content": content}
    if occurred_at:
        body["occurred_at"] = occurred_at
    if history_id is not None:
        return await call_java_api(
            api_name="manage_medical_history:update",
            path_params={"history_id": history_id},
            body=body,
            user_id=user_id,
        )
    return await call_java_api(api_name="manage_medical_history:create", body=body, user_id=user_id)


async def query_reports(report_id: int | None = None, user_id: int | None = None) -> dict[str, Any]:
    """查询检查报告列表或详情（带 report_id 时并行查详情+指标解读）。

    P2 优化：详情与指标解读是两个独立 Java 接口，原实现串行 await 延迟翻倍；
    现用 asyncio.gather 并发执行，总耗时收敛为较慢一方。
    """
    if report_id is not None:
        detail, interpretation = await asyncio.gather(
            call_java_api(
                api_name="query_reports:detail",
                path_params={"report_id": report_id},
                user_id=user_id,
            ),
            call_java_api(
                api_name="query_reports:interpretation",
                path_params={"report_id": report_id},
                user_id=user_id,
            ),
        )
        return {"detail": detail, "interpretation": interpretation}
    return await call_java_api(api_name="query_reports:list", user_id=user_id)


async def create_report(
    report_name: str,
    report_date: str,
    indicators: list[Any],
    patient_id: int | None = None,
    user_id: int | None = None,
) -> dict[str, Any]:
    """录入检查报告。"""
    body: dict[str, Any] = {
        "report_name": report_name,
        "report_date": report_date,
        "indicators": indicators,
    }
    if patient_id is not None:
        body["patient_id"] = patient_id
    return await call_java_api(tool_name="create_report", body=body, user_id=user_id)


async def query_medication_plans(
    status: str | None = None, user_id: int | None = None
) -> dict[str, Any]:
    """查询用药计划列表。"""
    params = {}
    if status:
        params["status"] = status
    return await call_java_api(tool_name="query_medication_plans", params=params, user_id=user_id)


async def update_medication_plan(
    plan_id: int, action: str, user_id: int | None = None
) -> dict[str, Any]:
    """暂停/恢复/完成用药计划。"""
    body = {"action": action}
    return await call_java_api(
        api_name="update_medication_plan",
        path_params={"plan_id": plan_id},
        body=body,
        user_id=user_id,
    )


async def query_follow_ups(status: str | None = None, user_id: int | None = None) -> dict[str, Any]:
    """查询随访计划列表。"""
    params = {}
    if status:
        params["status"] = status
    return await call_java_api(tool_name="query_follow_ups", params=params, user_id=user_id)


async def confirm_follow_up(
    follow_up_id: int, remind_at: str | None = None, user_id: int | None = None
) -> dict[str, Any]:
    """确认随访提醒时间。"""
    body = {}
    if remind_at:
        body["remind_at"] = remind_at
    return await call_java_api(
        api_name="confirm_follow_up",
        path_params={"follow_up_id": follow_up_id},
        body=body,
        user_id=user_id,
    )
