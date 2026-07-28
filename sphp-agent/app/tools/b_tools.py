"""B 端工具注册。

对应 PRD 7.2 工具调用矩阵中"面向 B 端医生"的能力。
"""

from app.tools.registry import (
    SecurityLevel,
    ToolDef,
    ToolScope,
    register,
)


def register_b_tools() -> None:
    """注册所有 B 端工具。在应用启动时调用。"""

    # ---- L1: 知识查询（无需确认） ----
    register(ToolDef(
        name="query_patient_history",
        description="查询接诊患者的历史病历、用药史、检查报告",
        scope=ToolScope.B_END,
        security_level=SecurityLevel.L1,
        requires_confirmation=False,
        api_method="GET",
        api_path="/api/admin/patient/history",
        param_schema={
            "patient_id": {"type": "integer", "description": "患者ID"},
        },
    ))

    register(ToolDef(
        name="check_drug_interaction",
        description="检测处方药品的禁忌与相互作用",
        scope=ToolScope.B_END,
        security_level=SecurityLevel.L1,
        requires_confirmation=False,
        api_method="POST",
        api_path="/api/admin/drug/interaction-check",
        param_schema={
            "drug_ids": {"type": "array", "items": {"type": "integer"}, "description": "药品ID列表"},
            "patient_id": {"type": "integer", "description": "患者ID（用于过敏史比对）"},
        },
    ))

    register(ToolDef(
        name="query_drug_guidelines",
        description="检索药品说明书、用药指南",
        scope=ToolScope.B_END,
        security_level=SecurityLevel.L1,
        requires_confirmation=False,
        api_method="GET",
        api_path="/api/admin/drug/guidelines",
        param_schema={
            "drug_name": {"type": "string", "description": "药品名称"},
        },
    ))

    register(ToolDef(
        name="recommend_prescription_template",
        description="根据诊断推荐常用处方模板供医生参考",
        scope=ToolScope.B_END,
        security_level=SecurityLevel.L1,
        requires_confirmation=False,
        api_method="GET",
        api_path="/api/admin/prescription/templates",
        param_schema={
            "diagnosis": {"type": "string", "description": "诊断关键词"},
        },
    ))

    # ---- L2: 病历整理（需医生确认） ----
    register(ToolDef(
        name="generate_draft_record",
        description="根据问诊记录生成病历草稿，供医生修改签名",
        scope=ToolScope.B_END,
        security_level=SecurityLevel.L2,
        requires_confirmation=True,
        api_method="POST",
        api_path="/api/admin/medical-record/draft",
        param_schema={
            "consultation_id": {"type": "integer", "description": "问诊记录ID"},
        },
    ))
