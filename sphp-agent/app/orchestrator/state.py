"""LangGraph 主图共享状态定义（系分 §7.1）。

AgentState 是图中唯一的共享状态对象，
通过 LangGraph 的 add_messages reducer 自动累积对话历史。
"""

from typing import Annotated, Any, Literal, NotRequired

from langgraph.graph import add_messages
from typing_extensions import TypedDict


class AgentState(TypedDict):
    """主图共享状态（系分 §7.1）。"""

    # 对话消息列表（LangGraph 内置 reducer，append 语义）
    # 元素为 dict（OpenAI 格式）或 LangChain BaseMessage，故用 Any
    messages: Annotated[list[Any], add_messages]

    # 会话唯一标识，首次对话时生成，随首个 done 事件返回前端
    session_id: str | None

    # 当前识别的业务意图：triage / registration / consultation / pharmacy / qa / chitchat
    # NotRequired：意图粘性依赖 checkpointer 跨轮保留 state.intent，_build_initial_state
    # 不传此字段（否则 None 覆盖历史意图），首轮由 intent_node 写入后跨轮保留。
    intent: NotRequired[str | None]

    # 从 JWT 鉴权获得的用户 ID，MCP 调用时注入 Header X-User-Id
    user_id: int | None

    # 当前服务端：c_end / b_end
    scope: str

    # B 端用户角色（ADMIN / DEPT_HEAD / DOCTOR），由 auth_node 从 B 端 token/parse 写入
    roles: list[str] | None

    # B 端用户所属科室 ID，由 auth_node 写入
    dept_id: int | None

    # B 端用户关联的医生 ID（b_doctor.id），由 auth_node 写入
    doctor_id: int | None

    # B 端医院 ID / C 端 context.hospital_id
    hospital_id: int | None

    # 当前问诊患者 ID（系分 §6.2 context.patient_id，M8-5）
    # B 端医生接诊时由前端 context 传入（医生工作台当前选中的就诊患者）；
    # C 端可选（就诊人切换时传入，健康工具 patient_id 选填默认本人）。
    # 由 _build_initial_state 提取写入，供 tool_caller 注入 LLM 上下文，
    # 并对 schema 必填 patient_id 的工具做确定性补全。
    patient_id: int | None

    # 用户收货地址 ID（context.address_id，对齐原始需求 §3 药店推荐）
    # C 端前端选中配送地址后注入；recommend_pharmacies 工具必填，由
    # _fill_missing_address_id 确定性补全、_build_address_context 注入 LLM 上下文。
    address_id: int | None

    # 前端受控预设动作，仅用于完成鉴权后直接执行允许的 L1 查询。
    preset_action: NotRequired[
        Literal[
            "interpret_prescription",
            "recommend_prescription_pharmacy",
            "notify_drug_order_paid",
        ]
        | None
    ]

    # 预设处方解读对应的处方 ID，由接入层完成格式校验后写入。
    preset_prescription_id: NotRequired[int | None]

    # 购药支付成功通知关联的订单 ID，仅允许受控预设写入。
    preset_drug_order_id: NotRequired[int | None]

    # 受控预设校验或推荐阶段的可展示失败提示，不交给模型补造。
    preset_error: NotRequired[str | None]

    # 非 L2 的业务交互卡，例如处方解读后的药店推荐入口。
    action_cards: NotRequired[list[dict[str, Any]] | None]

    # LLM 决定调用的工具列表，由 tool_caller 节点写入
    tool_calls: list[dict[str, Any]] | None

    # 工具执行结果列表（含成功和失败），由 tool_executor 写入
    tool_results: list[dict[str, Any]] | None

    # 待用户确认的 L2 操作列表，非空时 reply_node 推送 card 事件
    pending_confirmations: list[dict[str, Any]] | None

    # 在线问诊选医生候选（M8-6）：问诊场景 query_doctors 返回后，tool_caller
    # 拦截 save_pre_consultation 并把候选医生缓存至此，SSE 层推 options 选择卡。
    # 用户点选后作为普通消息回传（"我选择X医生"），tool_caller 用 _match_doctor_choice
    # 确定性匹配 doctor_id 注入 LLM 上下文，并清空本字段。
    # NotRequired：跨轮经 checkpointer 保留，_build_initial_state 不传（不污染下一轮）。
    pending_doctor_choices: NotRequired[list[dict[str, Any]] | None]

    # 本轮 RAG 检索到的医学知识上下文（不入 messages 历史，仅本次回复使用）
    rag_context: str | None

    # 风险标记，由 safety_check 节点追加
    risk_flags: list[str]

    # JWT Token（从请求 Header 提取，不含 "Bearer " 前缀），供 auth_node 调用 Java token/parse
    jwt_token: str | None

    # 工具调用迭代计数，子图循环用，防止无限循环
    tool_iteration: int | None
