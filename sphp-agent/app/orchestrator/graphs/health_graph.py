"""健康档案子图（系分 §3.1 场景五 + §5.2.1）。

调健康档案类工具：档案查询/报告管理/用药计划/随访/通知管理。
绑定健康档案场景工具白名单（12 个）：L1 查询类 5 个（档案/报告/用药
计划/随访/通知）+ L2 变更类 7 个（过敏史新增修改删除/既往史新增修改
删除/报告录入/用药计划更新/随访确认）。M8-2 补齐场景五工具路由——此前
10 个健康工具全部注册但无 intent 路由、4 业务子图白名单均不含（仅
triage 含 query_health_record 1 个），LLM 无路径可达。2026-08-10 新增
删除过敏史/删除既往史工具（Java HealthController 已实现 delete 接口）。

L2 工具的 card_type 复用 safety.py ``_map_card_type`` 既有映射
（confirm_allergy / confirm_medical_history / confirm_report /
confirm_medication_plan / confirm_follow_up），无需新增。
"""

from typing import Any

from app.orchestrator.graphs._common import build_tool_subgraph

# 健康档案场景工具白名单（系分 §3.1 场景五 + §5.2.1 健康链路）
HEALTH_TOOLS = [
    # L1 查询类（直接执行）
    "query_health_record",  # 健康档案查询（含过敏史、既往史）
    "query_reports",  # 检查报告查询
    "query_medication_plans",  # 用药计划查询
    "query_follow_ups",  # 随访计划查询
    "manage_notifications",  # 通知管理（list 查列表 / read 标已读）
    # L2 变更类（走 confirm_token 确认卡）
    "manage_allergy",  # 过敏史管理（新增/修改）
    "manage_medical_history",  # 既往史管理（新增/修改）
    "delete_allergy",  # 过敏史删除（2026-08-10）
    "delete_medical_history",  # 既往史删除（2026-08-10）
    "create_report",  # 检查报告录入
    "update_medication_plan",  # 用药计划状态更新（暂停/恢复/完成）
    "confirm_follow_up",  # 随访提醒确认
]


def build_health_graph() -> Any:
    """构造健康档案子图（编译后）。"""
    return build_tool_subgraph(tool_names=HEALTH_TOOLS)
