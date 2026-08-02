"""B 端工具 Schema 定义（系分 §5.3，共 9 个）。

启动时通过 register_b_tools() 注册到 ToolRegistry。
所有 API 路径以 /api/b 为前缀，MCP Server 内部拼接。
标注"本地"的工具不经 MCP Server，直接 pgvector 本地执行。
"""

from app.engine.tools.schema_registry import (
    SecurityLevel,
    ToolRegistry,
    ToolSchema,
    ToolScope,
)


def _register_patient_query_tools() -> None:
    """注册患者与药品查询类工具（2 个）：患者聚合信息与用药指南。"""

    ToolRegistry.register(
        ToolSchema(
            name="query_patient_history",
            description="聚合查询患者基本信息、过敏史、既往史、就诊记录、历史处方、当前用药",
            parameters={
                "properties": {
                    "patient_id": {"type": "integer", "description": "患者ID"},
                },
                "required": ["patient_id"],
            },
            scope=ToolScope.B_END,
            security_level=SecurityLevel.L1,
            executor="mcp",
        )
    )

    ToolRegistry.register(
        ToolSchema(
            name="query_drug_guide",
            description="查询药品说明书和用药指南。不可编造不存在的研究证据。",
            parameters={
                "properties": {
                    "drug_name": {"type": "string", "description": "药品名称"},
                },
                "required": ["drug_name"],
            },
            scope=ToolScope.B_END,
            security_level=SecurityLevel.L1,
            executor="mcp",
        )
    )


def _register_interaction_and_draft_tools() -> None:
    """注册接诊辅助类工具（2 个）：药物相互作用查询与病历草稿保存。"""

    ToolRegistry.register(
        ToolSchema(
            name="check_drug_interaction",
            description="查询药品相互作用（说明书+当前用药）。仅返回数据，结论由编排层生成。",
            parameters={
                "properties": {
                    "drug_names": {
                        "type": "array",
                        "items": {"type": "string"},
                        "description": "药品名称列表",
                    },
                    "patient_id": {"type": "integer", "description": "患者ID"},
                },
                "required": ["drug_names", "patient_id"],
            },
            scope=ToolScope.B_END,
            security_level=SecurityLevel.L1,
            executor="mcp",
        )
    )

    ToolRegistry.register(
        ToolSchema(
            name="generate_draft_note",
            description="保存病历草稿（LLM 生成，工具持久化）。仅供医生修改，不可签名。",
            parameters={
                "properties": {
                    "consultation_id": {"type": "integer", "description": "问诊记录ID"},
                    "note_content": {"type": "string", "description": "病历草稿文本"},
                },
                "required": ["consultation_id", "note_content"],
            },
            scope=ToolScope.B_END,
            security_level=SecurityLevel.L2,
            executor="mcp",
        )
    )


def _register_prescription_check_tools() -> None:
    """注册处方审核类工具（3 个）：禁忌查询、过敏风险、重复用药检查。"""

    ToolRegistry.register(
        ToolSchema(
            name="check_contraindication",
            description="查询药品禁忌信息 + 患者过敏史。仅返回数据，禁忌判断由编排层 LLM 生成。",
            parameters={
                "properties": {
                    "drug_name": {"type": "string", "description": "药品名称"},
                    "patient_id": {"type": "integer", "description": "患者ID"},
                },
                "required": ["drug_name", "patient_id"],
            },
            scope=ToolScope.B_END,
            security_level=SecurityLevel.L1,
            executor="mcp",
        )
    )

    ToolRegistry.register(
        ToolSchema(
            name="check_allergy_risk",
            description="查询患者过敏史记录。对应患者过敏记录。",
            parameters={
                "properties": {
                    "drug_name": {"type": "string", "description": "药品名称"},
                    "patient_id": {"type": "integer", "description": "患者ID"},
                },
                "required": ["drug_name", "patient_id"],
            },
            scope=ToolScope.B_END,
            security_level=SecurityLevel.L1,
            executor="mcp",
        )
    )

    ToolRegistry.register(
        ToolSchema(
            name="check_duplicate_medication",
            description="查询患者当前用药清单。仅返回数据，重复判断由编排层 LLM 生成。",
            parameters={
                "properties": {
                    "drug_name": {"type": "string", "description": "药品名称"},
                    "patient_id": {"type": "integer", "description": "患者ID"},
                },
                "required": ["drug_name", "patient_id"],
            },
            scope=ToolScope.B_END,
            security_level=SecurityLevel.L1,
            executor="mcp",
        )
    )


def _register_recommend_and_report_tools() -> None:
    """注册导诊推荐与报告解读类工具（2 个）。"""

    ToolRegistry.register(
        ToolSchema(
            name="recommend_care",
            description="查询科室列表及对应医生号源。仅返回数据，推荐结论由编排层 LLM 生成。",
            parameters={
                "properties": {
                    "department_id": {"type": "integer", "description": "科室ID（选填）"},
                },
                "required": [],
            },
            scope=ToolScope.B_END,
            security_level=SecurityLevel.L1,
            executor="mcp",
        )
    )

    # 报告解读为本地工具（pgvector），不经过 MCP Server
    ToolRegistry.register(
        ToolSchema(
            name="interpret_report",
            description="本地 pgvector 检索参考范围。仅返回数据，通俗化解读由编排层生成。",
            parameters={
                "properties": {
                    "report_content": {"type": "string", "description": "报告内容文本"},
                    "report_type": {"type": "string", "description": "报告类型（选填）"},
                },
                "required": ["report_content"],
            },
            scope=ToolScope.B_END,
            security_level=SecurityLevel.L1,
            executor="local",
        )
    )


def register_b_tools() -> None:
    """注册所有 B 端工具（9 个），在应用启动时调用。

    按业务域拆分为 4 个私有子函数，每个子函数注册一组工具。
    """
    _register_patient_query_tools()
    _register_interaction_and_draft_tools()
    _register_prescription_check_tools()
    _register_recommend_and_report_tools()
