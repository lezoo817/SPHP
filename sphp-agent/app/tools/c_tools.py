"""C 端工具注册。

对应 PRD 7.2 工具调用矩阵中"面向 C 端患者"的能力。
每个工具的 security_level 和 requires_confirmation 严格按 PRD 定义。
"""

from app.tools.registry import (
    SecurityLevel,
    ToolDef,
    ToolScope,
    register,
)


def register_c_tools() -> None:
    """注册所有 C 端工具。在应用启动时调用。"""

    # ---- L1: 信息查询（无需确认） ----
    register(ToolDef(
        name="query_available_slots",
        description="查询号源余量，查看某医生某天的可预约时段",
        scope=ToolScope.C_END,
        security_level=SecurityLevel.L1,
        requires_confirmation=False,
        api_method="GET",
        api_path="/api/registration/slots",
        param_schema={
            "doctor_id": {"type": "integer", "description": "医生ID"},
            "date": {"type": "string", "description": "日期 YYYY-MM-DD"},
        },
    ))

    register(ToolDef(
        name="query_hospital_info",
        description="查询医院、科室、医生的基础信息",
        scope=ToolScope.C_END,
        security_level=SecurityLevel.L1,
        requires_confirmation=False,
        api_method="GET",
        api_path="/api/hospital/info",
        param_schema={
            "hospital_id": {"type": "integer", "description": "医院ID（可选）"},
            "department_id": {"type": "integer", "description": "科室ID（可选）"},
        },
    ))

    register(ToolDef(
        name="query_pharmacy_stock",
        description="查询指定药品在附近药店的库存",
        scope=ToolScope.C_END,
        security_level=SecurityLevel.L1,
        requires_confirmation=False,
        api_method="GET",
        api_path="/api/order/pharmacy-stock",
        param_schema={
            "drug_name": {"type": "string", "description": "药品名称"},
            "location": {"type": "string", "description": "用户位置（可选）"},
        },
    ))

    register(ToolDef(
        name="interpret_prescription",
        description="用通俗语言解读处方内容（药品用途、用法用量、注意事项）",
        scope=ToolScope.C_END,
        security_level=SecurityLevel.L1,
        requires_confirmation=False,
        api_method="GET",
        api_path="/api/consultation/prescription/interpret",
        param_schema={
            "prescription_id": {"type": "integer", "description": "处方ID"},
        },
    ))

    register(ToolDef(
        name="interpret_report",
        description="结构化解析检查报告指标，用通俗语言说明含义",
        scope=ToolScope.C_END,
        security_level=SecurityLevel.L1,
        requires_confirmation=False,
        api_method="POST",
        api_path="/api/health/report/interpret",
        param_schema={
            "report_id": {"type": "integer", "description": "报告ID"},
        },
    ))

    # ---- L2: 业务操作（需用户确认） ----
    register(ToolDef(
        name="create_registration",
        description="创建挂号单，锁定号源（预扣）",
        scope=ToolScope.C_END,
        security_level=SecurityLevel.L2,
        requires_confirmation=True,
        api_method="POST",
        api_path="/api/registration/create",
        param_schema={
            "doctor_id": {"type": "integer", "description": "医生ID"},
            "slot_id": {"type": "integer", "description": "时段ID"},
            "patient_id": {"type": "integer", "description": "就诊人ID"},
        },
    ))

    register(ToolDef(
        name="submit_patient_summary",
        description="提交预问诊摘要给医生（挂号后自动触发）",
        scope=ToolScope.C_END,
        security_level=SecurityLevel.L2,
        requires_confirmation=True,
        api_method="POST",
        api_path="/api/consultation/summary/submit",
        param_schema={
            "registration_id": {"type": "integer", "description": "挂号记录ID"},
            "summary": {"type": "string", "description": "结构化病情摘要"},
        },
    ))

    register(ToolDef(
        name="create_medication_order",
        description="创建购药订单（草稿状态，待付款）",
        scope=ToolScope.C_END,
        security_level=SecurityLevel.L2,
        requires_confirmation=True,
        api_method="POST",
        api_path="/api/order/create",
        param_schema={
            "prescription_id": {"type": "integer", "description": "处方ID"},
            "pharmacy_id": {"type": "integer", "description": "药店ID"},
        },
    ))

    register(ToolDef(
        name="create_medication_reminder",
        description="根据处方设置用药提醒计划",
        scope=ToolScope.C_END,
        security_level=SecurityLevel.L2,
        requires_confirmation=True,
        api_method="POST",
        api_path="/api/health/reminder/create",
        param_schema={
            "prescription_id": {"type": "integer", "description": "处方ID"},
        },
    ))

    register(ToolDef(
        name="create_follow_up_plan",
        description="根据医生建议创建复诊/复查随访计划",
        scope=ToolScope.C_END,
        security_level=SecurityLevel.L2,
        requires_confirmation=True,
        api_method="POST",
        api_path="/api/health/follow-up/create",
        param_schema={
            "consultation_id": {"type": "integer", "description": "问诊记录ID"},
        },
    ))
