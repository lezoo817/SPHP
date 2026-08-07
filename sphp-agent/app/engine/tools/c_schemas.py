"""C 端工具 Schema 定义（系分 §5.3，共 31 个）。

启动时通过 register_c_tools() 注册到 ToolRegistry。
所有 API 路径以 /api/c/v1 为前缀，MCP Server 内部拼接。
"""

from app.engine.tools.schema_registry import (
    SecurityLevel,
    ToolRegistry,
    ToolSchema,
    ToolScope,
)


def _register_triage_tools() -> None:
    """注册导诊类工具（2 个）：症状评估与医疗知识检索。"""

    ToolRegistry.register(
        ToolSchema(
            name="create_triage_assessment",
            description="提交症状进行导诊评估，返回紧急程度与推荐科室。不可下诊断结论；急危重症识别后引导线下就医。",
            parameters={
                "properties": {
                    "hospital_id": {"type": "integer", "description": "医院ID"},
                    "symptom": {"type": "string", "description": "主要症状描述"},
                    "duration": {"type": "string", "description": "症状持续时间（选填）"},
                    "temperature": {"type": "number", "description": "体温（选填）"},
                    "medical_history": {"type": "string", "description": "既往病史（选填）"},
                },
                "required": ["hospital_id", "symptom"],
            },
            scope=ToolScope.C_END,
            security_level=SecurityLevel.L1,
            executor="mcp",
        )
    )

    # search_medical_knowledge 为本地工具（pgvector），不经 MCP Server
    ToolRegistry.register(
        ToolSchema(
            name="search_medical_knowledge",
            description="检索医疗科普知识库，获取疾病、药品、健康相关的权威科普内容。引用来源，标注AI建议仅供参考。",
            parameters={
                "properties": {
                    "query": {"type": "string", "description": "检索问题，如'高血压日常注意事项'"},
                    "top_k": {"type": "integer", "description": "返回条数，默认5"},
                },
                "required": ["query"],
            },
            scope=ToolScope.C_END,
            security_level=SecurityLevel.L1,
            executor="local",
        )
    )


def _register_appointment_query_tools() -> None:
    """注册挂号查询类工具（3 个）：科室、医生、号源时段查询。"""

    ToolRegistry.register(
        ToolSchema(
            name="query_departments",
            description="查询科室列表",
            parameters={
                "properties": {
                    "hospital_id": {"type": "integer", "description": "医院ID"},
                    "keyword": {"type": "string", "description": "科室名称关键词（选填）"},
                },
                "required": ["hospital_id"],
            },
            scope=ToolScope.C_END,
            security_level=SecurityLevel.L1,
            executor="mcp",
        )
    )

    ToolRegistry.register(
        ToolSchema(
            name="query_doctors",
            description="按科室查询医生列表。返回的 availableCount 是该医生指定日期线下门诊"
            "的号源余量，仅供挂号场景判断能否挂号；在线问诊不依赖号源（异步问诊随时可发起），"
            "即使 availableCount=0 也可选该医生发起在线问诊。",
            parameters={
                "properties": {
                    "hospital_id": {"type": "integer", "description": "医院ID"},
                    "department_id": {"type": "integer", "description": "科室ID"},
                    "date": {
                        "type": "string",
                        "description": "日期 YYYY-MM-DD（选填，挂号场景用于查该日号源余量）",
                    },
                },
                "required": ["hospital_id", "department_id"],
            },
            scope=ToolScope.C_END,
            security_level=SecurityLevel.L1,
            executor="mcp",
        )
    )

    ToolRegistry.register(
        ToolSchema(
            name="query_schedule_slots",
            description="查询医生可预约时段与余量",
            parameters={
                "properties": {
                    "doctor_id": {"type": "integer", "description": "医生ID"},
                    "hospital_id": {"type": "integer", "description": "医院ID"},
                    "date": {"type": "string", "description": "日期 YYYY-MM-DD"},
                },
                "required": ["doctor_id", "hospital_id", "date"],
            },
            scope=ToolScope.C_END,
            security_level=SecurityLevel.L1,
            executor="mcp",
        )
    )


def _register_appointment_order_tools() -> None:
    """注册挂号订单类工具（3 个）：创建、查询、取消挂号订单。"""

    ToolRegistry.register(
        ToolSchema(
            name="create_appointment",
            description="创建挂号锁定订单（预扣号源）。抢号失败提示已被抢完并推荐同科室医生。",
            parameters={
                "properties": {
                    "hospital_id": {"type": "integer", "description": "医院ID"},
                    "slot_id": {"type": "integer", "description": "时段ID"},
                    "patient_id": {"type": "integer", "description": "就诊人ID（选填）"},
                },
                "required": ["hospital_id", "slot_id"],
            },
            scope=ToolScope.C_END,
            security_level=SecurityLevel.L2,
            executor="mcp",
        )
    )

    ToolRegistry.register(
        ToolSchema(
            name="query_appointments",
            description="查询挂号订单列表或详情（不带 appointment_id 返回列表，带 id 返回详情）",
            parameters={
                "properties": {
                    "appointment_id": {
                        "type": "integer",
                        "description": "挂号订单ID（选填，不传返回列表）",
                    },
                    "status": {"type": "string", "description": "订单状态筛选（选填）"},
                },
                "required": [],
            },
            scope=ToolScope.C_END,
            security_level=SecurityLevel.L1,
            executor="mcp",
        )
    )

    ToolRegistry.register(
        ToolSchema(
            name="cancel_appointment",
            description="取消挂号锁定订单",
            parameters={
                "properties": {
                    "appointment_id": {"type": "integer", "description": "挂号订单ID"},
                },
                "required": ["appointment_id"],
            },
            scope=ToolScope.C_END,
            security_level=SecurityLevel.L2,
            executor="mcp",
        )
    )


def _register_waitlist_payment_tools() -> None:
    """注册候补与支付查询类工具（2 个）。"""

    ToolRegistry.register(
        ToolSchema(
            name="join_waitlist",
            description="号源约满时登记候补，有名额释放自动通知",
            parameters={
                "properties": {
                    "slot_id": {"type": "integer", "description": "时段ID"},
                    "patient_id": {"type": "integer", "description": "就诊人ID（选填）"},
                },
                "required": ["slot_id"],
            },
            scope=ToolScope.C_END,
            security_level=SecurityLevel.L2,
            executor="mcp",
        )
    )

    ToolRegistry.register(
        ToolSchema(
            name="query_payment_status",
            description="查询支付单状态。Agent 不可代付，用户手动输密码。",
            parameters={
                "properties": {
                    "payment_id": {"type": "integer", "description": "支付单ID"},
                },
                "required": ["payment_id"],
            },
            scope=ToolScope.C_END,
            security_level=SecurityLevel.L1,
            executor="mcp",
        )
    )


def _register_consultation_tools() -> None:
    """注册问诊管理类工具（3 个）：预问诊、问诊查询、发送消息。"""

    ToolRegistry.register(
        ToolSchema(
            name="save_pre_consultation",
            description="提交预问诊摘要给选定的医生（submit=true 提交，false 存草稿）。"
            "doctor_id 必须来自 query_doctors 结果，禁止编造。"
            "过敏史由 Java 端按患者健康档案关联，无需传入。",
            parameters={
                "properties": {
                    "doctor_id": {
                        "type": "integer",
                        "description": "接诊医生ID（来自 query_doctors，禁止编造）",
                    },
                    "chief_complaint": {"type": "string", "description": "主诉"},
                    "submit": {"type": "boolean", "description": "true=提交，false=存草稿"},
                },
                "required": ["doctor_id", "chief_complaint", "submit"],
            },
            scope=ToolScope.C_END,
            security_level=SecurityLevel.L2,
            executor="mcp",
        )
    )

    ToolRegistry.register(
        ToolSchema(
            name="query_consultations",
            description="查询问诊记录列表或详情（不传 id 返回列表，传 id 返回详情及消息）",
            parameters={
                "properties": {
                    "consultation_id": {"type": "integer", "description": "问诊记录ID（选填）"},
                    "status": {"type": "string", "description": "状态筛选（选填）"},
                },
                "required": [],
            },
            scope=ToolScope.C_END,
            security_level=SecurityLevel.L1,
            executor="mcp",
        )
    )

    ToolRegistry.register(
        ToolSchema(
            name="send_consultation_message",
            description="发送问诊文字消息",
            parameters={
                "properties": {
                    "consultation_id": {"type": "integer", "description": "问诊记录ID"},
                    "content": {"type": "string", "description": "消息内容"},
                },
                "required": ["consultation_id", "content"],
            },
            scope=ToolScope.C_END,
            security_level=SecurityLevel.L2,
            executor="mcp",
        )
    )


def _register_prescription_tools() -> None:
    """注册处方管理类工具（2 个）：处方查询与解读。"""

    ToolRegistry.register(
        ToolSchema(
            name="query_prescriptions",
            description="查询处方列表或详情（不传 id 返回列表，传 id 返回详情及药品明细）",
            parameters={
                "properties": {
                    "prescription_id": {"type": "integer", "description": "处方ID（选填）"},
                    "patient_id": {"type": "integer", "description": "就诊人ID（选填）"},
                    "recent_days": {"type": "integer", "description": "最近天数（选填，最大30）"},
                },
                "required": [],
            },
            scope=ToolScope.C_END,
            security_level=SecurityLevel.L1,
            executor="mcp",
        )
    )

    ToolRegistry.register(
        ToolSchema(
            name="interpret_prescription",
            description="通俗解读处方药品。不可修改处方；Agent 无权开方；标注AI建议仅供参考。",
            parameters={
                "properties": {
                    "prescription_id": {"type": "integer", "description": "处方ID"},
                },
                "required": ["prescription_id"],
            },
            scope=ToolScope.C_END,
            security_level=SecurityLevel.L1,
            executor="mcp",
        )
    )


def _register_pharmacy_query_tools() -> None:
    """注册购药查询与下单类工具（4 个）：库存查询、药店推荐、创建订单、订单查询。"""

    ToolRegistry.register(
        ToolSchema(
            name="query_pharmacy_stock",
            description="查询附近药店库存与价格",
            parameters={
                "properties": {
                    "prescription_id": {"type": "integer", "description": "处方ID"},
                    "patient_id": {"type": "integer", "description": "就诊人ID（选填）"},
                },
                "required": ["prescription_id"],
            },
            scope=ToolScope.C_END,
            security_level=SecurityLevel.L1,
            executor="mcp",
        )
    )

    ToolRegistry.register(
        ToolSchema(
            name="recommend_pharmacies",
            description="推荐可配送院内药店（Java 服务端按价格/距离/配送时效加权排序）。"
            "对齐原始需求 §3 药店推荐，优先于 query_pharmacy_stock 使用。",
            parameters={
                "properties": {
                    "prescription_id": {"type": "integer", "description": "处方ID"},
                    "address_id": {"type": "integer", "description": "用户收货地址ID"},
                    "patient_id": {"type": "integer", "description": "就诊人ID（选填）"},
                    "sort": {"type": "string", "description": "排序方式（选填）"},
                },
                "required": ["prescription_id", "address_id"],
            },
            scope=ToolScope.C_END,
            security_level=SecurityLevel.L1,
            executor="mcp",
        )
    )

    ToolRegistry.register(
        ToolSchema(
            name="create_drug_order",
            description="创建购药订单草稿（待付款状态）",
            parameters={
                    "properties": {
                        "prescription_id": {"type": "integer", "description": "处方ID"},
                        "pharmacy_id": {"type": "integer", "description": "药店ID"},
                        "address_id": {"type": "integer", "description": "当前账号收货地址ID"},
                        "patient_id": {"type": "integer", "description": "就诊人ID（选填）"},
                    },
                    "required": ["prescription_id", "pharmacy_id", "address_id"],
            },
            scope=ToolScope.C_END,
            security_level=SecurityLevel.L2,
            executor="mcp",
        )
    )

    ToolRegistry.register(
        ToolSchema(
            name="authorize_drug_order_reminder_after_receipt",
            description="登记购药订单确认收货后自动开启用药提醒",
            parameters={
                "properties": {
                    "drug_order_id": {"type": "integer", "description": "已支付购药订单ID"},
                },
                "required": ["drug_order_id"],
            },
            scope=ToolScope.C_END,
            security_level=SecurityLevel.L2,
            executor="mcp",
        )
    )

    ToolRegistry.register(
        ToolSchema(
            name="query_drug_orders",
            description="查询购药订单列表或详情（不带 drug_order_id 返回列表，带 id 返回详情）",
            parameters={
                "properties": {
                    "drug_order_id": {"type": "integer", "description": "购药订单ID（选填）"},
                    "status": {"type": "string", "description": "状态筛选（选填）"},
                    "logistics_status": {"type": "string", "description": "物流状态筛选（选填）"},
                },
                "required": [],
            },
            scope=ToolScope.C_END,
            security_level=SecurityLevel.L1,
            executor="mcp",
        )
    )


def _register_pharmacy_order_tools() -> None:
    """注册购药订单操作类工具（2 个）：取消订单与确认收货。"""

    ToolRegistry.register(
        ToolSchema(
            name="cancel_drug_order",
            description="取消未支付购药订单",
            parameters={
                "properties": {
                    "drug_order_id": {"type": "integer", "description": "购药订单ID"},
                },
                "required": ["drug_order_id"],
            },
            scope=ToolScope.C_END,
            security_level=SecurityLevel.L2,
            executor="mcp",
        )
    )

    ToolRegistry.register(
        ToolSchema(
            name="confirm_drug_receipt",
            description="确认购药收货",
            parameters={
                "properties": {
                    "drug_order_id": {"type": "integer", "description": "购药订单ID"},
                },
                "required": ["drug_order_id"],
            },
            scope=ToolScope.C_END,
            security_level=SecurityLevel.L2,
            executor="mcp",
        )
    )


def _register_health_record_tools() -> None:
    """注册健康档案类工具：病历解读、健康档案、过敏史和既往史管理。"""

    ToolRegistry.register(
        ToolSchema(
            name="query_medical_records",
            description="查询医生病历列表，可按就诊人和最近天数筛选",
            parameters={
                "properties": {
                    "patient_id": {"type": "integer", "description": "就诊人ID（选填）"},
                    "recent_days": {"type": "integer", "description": "最近天数（选填，最大30）"},
                },
                "required": [],
            },
            scope=ToolScope.C_END,
            security_level=SecurityLevel.L1,
            executor="mcp",
        )
    )

    ToolRegistry.register(
        ToolSchema(
            name="interpret_medical_record",
            description="读取医生病历正文并结合该病历所属就诊人的过敏史、既往史生成解读输入",
            parameters={
                "properties": {
                    "consult_id": {"type": "integer", "description": "病历对应的完成问诊记录ID"},
                },
                "required": ["consult_id"],
            },
            scope=ToolScope.C_END,
            security_level=SecurityLevel.L1,
            executor="mcp",
        )
    )

    ToolRegistry.register(
        ToolSchema(
            name="query_health_record",
            description="查询健康档案（含过敏史、既往史）",
            parameters={
                "properties": {
                    "patient_id": {"type": "integer", "description": "就诊人ID（选填）"},
                },
                "required": [],
            },
            scope=ToolScope.C_END,
            security_level=SecurityLevel.L1,
            executor="mcp",
        )
    )

    ToolRegistry.register(
        ToolSchema(
            name="manage_allergy",
            description="管理过敏史记录（不带 allergy_id 新增，带 allergy_id 修改）",
            parameters={
                "properties": {
                    "allergy_id": {
                        "type": "integer",
                        "description": "过敏记录ID（选填，不传=新增）",
                    },
                    "allergen": {"type": "string", "description": "过敏原"},
                    "reaction": {"type": "string", "description": "反应描述（选填）"},
                },
                "required": ["allergen"],
            },
            scope=ToolScope.C_END,
            security_level=SecurityLevel.L2,
            executor="mcp",
        )
    )

    ToolRegistry.register(
        ToolSchema(
            name="manage_medical_history",
            description="管理既往史记录（不带 history_id 新增，带 history_id 修改）",
            parameters={
                "properties": {
                    "history_id": {
                        "type": "integer",
                        "description": "既往史记录ID（选填，不传=新增）",
                    },
                    "content": {"type": "string", "description": "病史内容"},
                    "occurred_at": {"type": "string", "description": "发生日期（选填）"},
                },
                "required": ["content"],
            },
            scope=ToolScope.C_END,
            security_level=SecurityLevel.L2,
            executor="mcp",
        )
    )


def _register_report_tools() -> None:
    """注册检查报告类工具（2 个）：报告查询与录入。"""

    ToolRegistry.register(
        ToolSchema(
            name="query_reports",
            description="查询检查报告列表或详情（带 report_id 时包含指标解读）",
            parameters={
                "properties": {
                    "report_id": {"type": "integer", "description": "报告ID（选填，不传返回列表）"},
                },
                "required": [],
            },
            scope=ToolScope.C_END,
            security_level=SecurityLevel.L1,
            executor="mcp",
        )
    )

    ToolRegistry.register(
        ToolSchema(
            name="create_report",
            description="录入检查报告（indicators 含 name/value/unit/reference_range）",
            parameters={
                "properties": {
                    "report_name": {"type": "string", "description": "报告名称"},
                    "report_date": {"type": "string", "description": "报告日期 YYYY-MM-DD"},
                    "indicators": {
                        "type": "array",
                        "items": {
                            "type": "object",
                            "properties": {
                                "name": {"type": "string"},
                                "value": {"type": "string"},
                                "unit": {"type": "string"},
                                "reference_range": {"type": "string"},
                            },
                        },
                        "description": "检查指标列表",
                    },
                    "patient_id": {"type": "integer", "description": "就诊人ID（选填）"},
                },
                "required": ["report_name", "report_date", "indicators"],
            },
            scope=ToolScope.C_END,
            security_level=SecurityLevel.L2,
            executor="mcp",
        )
    )


def _register_medication_plan_tools() -> None:
    """注册用药计划类工具（2 个）：计划查询与状态更新。"""

    ToolRegistry.register(
        ToolSchema(
            name="query_medication_plans",
            description="查询用药计划列表",
            parameters={
                "properties": {
                    "status": {"type": "string", "description": "状态筛选（选填）"},
                },
                "required": [],
            },
            scope=ToolScope.C_END,
            security_level=SecurityLevel.L1,
            executor="mcp",
        )
    )

    ToolRegistry.register(
        ToolSchema(
            name="update_medication_plan",
            description="更新用药计划：开启/关闭用药提醒、暂停/恢复/完成计划",
            parameters={
                "properties": {
                    "plan_id": {"type": "integer", "description": "用药计划ID"},
                    "action": {
                        "type": "string",
                        "enum": [
                            "ENABLE_REMINDER",
                            "DISABLE_REMINDER",
                            "PAUSE",
                            "RESUME",
                            "COMPLETE",
                        ],
                        "description": "操作类型：ENABLE_REMINDER=开启用药提醒，"
                        "DISABLE_REMINDER=关闭用药提醒，PAUSE=暂停，RESUME=恢复，"
                        "COMPLETE=完成",
                    },
                },
                "required": ["plan_id", "action"],
            },
            scope=ToolScope.C_END,
            security_level=SecurityLevel.L2,
            executor="mcp",
        )
    )


def _register_follow_up_tools() -> None:
    """注册随访与通知类工具（3 个）：随访查询、确认随访、通知管理。"""

    ToolRegistry.register(
        ToolSchema(
            name="query_follow_ups",
            description="查询随访计划列表",
            parameters={
                "properties": {
                    "status": {"type": "string", "description": "状态筛选（选填）"},
                },
                "required": [],
            },
            scope=ToolScope.C_END,
            security_level=SecurityLevel.L1,
            executor="mcp",
        )
    )

    ToolRegistry.register(
        ToolSchema(
            name="confirm_follow_up",
            description="确认随访提醒时间",
            parameters={
                "properties": {
                    "follow_up_id": {"type": "integer", "description": "随访计划ID"},
                    "remind_at": {"type": "string", "description": "提醒时间（选填）"},
                },
                "required": ["follow_up_id"],
            },
            scope=ToolScope.C_END,
            security_level=SecurityLevel.L2,
            executor="mcp",
        )
    )

    ToolRegistry.register(
        ToolSchema(
            name="manage_notifications",
            description="管理通知（action=list 查列表，action=read 标已读）",
            parameters={
                "properties": {
                    "action": {
                        "type": "string",
                        "enum": ["list", "read"],
                        "description": "操作类型",
                    },
                    "notification_id": {
                        "type": "integer",
                        "description": "通知ID（action=read 时必填）",
                    },
                },
                "required": ["action"],
            },
            scope=ToolScope.C_END,
            security_level=SecurityLevel.L1,
            executor="mcp",
        )
    )


def register_c_tools() -> None:
    """注册所有 C 端工具（30 个），在应用启动时调用。

    按业务域拆分为 12 个私有子函数，每个子函数注册一组工具。
    """
    _register_triage_tools()
    _register_appointment_query_tools()
    _register_appointment_order_tools()
    _register_waitlist_payment_tools()
    _register_consultation_tools()
    _register_prescription_tools()
    _register_pharmacy_query_tools()
    _register_pharmacy_order_tools()
    _register_health_record_tools()
    _register_report_tools()
    _register_medication_plan_tools()
    _register_follow_up_tools()
