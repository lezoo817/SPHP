"""工具调用决策节点（系分 §5.1 / §5.2.2）。

LLM 决策选择哪个 Function Calling 工具及参数，返回 ``tool_calls`` 供
safety_check / tool_executor 消费。

安全策略（双层防护）：
    1. 绑定 L1/L2 工具给 LLM（L3/L4 不注册）
    2. 即便 LLM 返回 L3/L4 工具名，也过滤掉，只放行 L1/L2
L2 工具由 safety_check 拦截生成确认卡片；L3/L4 被双层过滤。
LLM 未选工具或解析失败时降级为 ``tool_calls=[]``，由回复节点兜底，不阻塞流程。
"""

import logging
from datetime import date
from typing import Any

from app.engine.llm.factory import build_llm
from app.engine.memory.buffer import truncate_messages
from app.engine.tools.schema_registry import SecurityLevel, ToolRegistry, ToolScope
from app.infrastructure.config.settings import get_settings
from app.orchestrator.state import AgentState

logger = logging.getLogger(__name__)

# C 端项目固定的科室清单（2026-08-06 修订）：15 个常见科室（3-科室医生种子数据.sql，
# id 100-114，其中妇产科改为妇科）+ 男科/中医科（id 115/116），不含内科/外科。
# 分诊/问诊/挂号推荐科室时，query_departments 的 keyword 只能从这些名称中选择，
# 禁止使用别名（如"心内科"→"心血管内科"、"消化科"→"消化内科"），否则 keyword
# 查不到科室。
DEPARTMENT_LIST = [
    "全科",
    "呼吸内科",
    "消化内科",
    "心血管内科",
    "神经内科",
    "内分泌科",
    "普通外科",
    "骨科",
    "泌尿外科",
    "妇科",
    "儿科",
    "眼科",
    "耳鼻喉科",
    "口腔科",
    "皮肤科",
    "男科",
    "中医科",
]

# 工具决策系统提示词
TOOL_CALLER_SYSTEM_PROMPT = """你是医疗平台的工具调用助手。

当前日期：{today}（服务器本地日期，YYYY-MM-DD）

你可以使用以下工具来完成用户请求（只使用列表内的工具）：

{tools_desc}

规则：
1. 当用户请求涉及业务操作（创建/修改/取消/查询）时，必须调用对应的工具完成，不要仅凭知识回复
2. 无依赖的工具可一次并行调用；有依赖的工具分步调用：
   - 查科室、查医生、查排班等查询可一次并行
   - 创建类工具（如创建挂号）的必填参数（如 slot_id）来自查询结果，
     必须先执行查询、等结果返回后再调用，禁止在首次并行中编造该参数
3. 参数严格按工具定义填写，缺失的信息先询问用户，或等上一轮工具结果返回后再决策
4. 不要编造工具名或参数
5. **日期参数必须用当前日期或之后的日期**：用户说"今天"即 {today}；说"X月X日"若未给出年份，
   默认当年，且不得早于今天。禁止编造过去日期
6. 工具调用结果会自动返回，不需要让用户等待重试
7. 创建/修改/取消类操作（如创建挂号、取消挂号）在拿到所需参数后**直接调用对应工具**，
   不要用自然语言反问用户"是否确认"——用户请求即代表发起授权，系统会通过确认卡片
   让用户最终确认，你只需调用工具即可
8. **完成判断（每次决策前先检查）**：
   - 如果上一步工具结果已返回用户所需的全部信息，**停止调用工具**
   - 如果用户请求包含创建/修改/取消操作（如"挂X的号"），查询步骤只是前置，
     拿到所需参数后**必须继续**调用对应创建/修改/取消工具，不要提前停止
   - 不要重复调用已执行过的工具（相同参数、相同目的）
9. 一次只推进一个必要的查询/操作步骤，避免一次轮询所有信息
10. **医院 ID（hospital_id）**：查科室/查医生/查排班/创建挂号/导诊分诊等工具必填
    hospital_id。系统上下文已给出当前医院 ID 时直接使用；未给出且用户未说明医院时，
    先询问用户在哪家医院，禁止编造 hospital_id"""

# B 端工具决策系统提示词（M8-4）：C 端患者语义之上，追加医生身份/患者隐私/草稿边界。
# 医疗免责声明由前端卡片统一承担，agent 回复不再注入。
B_TOOL_CALLER_SYSTEM_PROMPT = """你是医疗平台的医生工作台助手，服务对象是**医生本人**（B 端）。

当前日期：{today}（服务器本地日期，YYYY-MM-DD）

你可以使用以下工具来完成医生请求（只使用列表内的工具）：

{tools_desc}

规则：
1. 当医生请求涉及业务操作（查询/生成/检查）时，必须调用对应的工具完成，不要仅凭知识回复
2. 无依赖的工具可一次并行调用；有依赖的工具分步调用（先查患者/用药，再基于结果做
   相互作用/禁忌/过敏风险等检查，参数来自前置查询结果，禁止在首次并行中编造）
3. 参数严格按工具定义填写，缺失的信息先询问医生，或等上一轮工具结果返回后再决策
4. 不要编造工具名或参数
5. 查询患者相关数据（过敏史/用药/档案）时，**必须携带当前接诊患者 patient_id**；
   医生未提供时先向医生索要，禁止编造或猜测患者 ID
6. 患者数据仅用于本次接诊决策，回复中不泄露敏感信息（脱敏展示）
7. **草稿边界**：病历草稿（generate_draft_note）仅供医生修改确认，不可替代医生签名；
   工具只保存草稿，医生最终确认由接诊流程人工完成
8. 创建/修改类操作（如保存病历草稿）在拿到所需参数后**直接调用对应工具**，
   不要用自然语言反问医生"是否确认"——医生请求即代表发起授权，系统会通过确认卡片
   让医生最终确认，你只需调用工具即可
9. **完成判断（每次决策前先检查）**：
   - 如果上一步工具结果已返回医生所需的全部信息，**停止调用工具**
   - 不要重复调用已执行过的工具（相同参数、相同目的）
10. 一次只推进一个必要的查询/检查步骤，避免一次轮询所有信息"""


def _build_tools_prompt(tools: list[dict[str, Any]]) -> str:
    """将工具 Schema 列表格式化为 prompt 描述。"""
    lines = []
    for tool in tools:
        fn = tool.get("function", {})
        name = fn.get("name", "")
        desc = fn.get("description", "")
        params = fn.get("parameters", {})
        props = params.get("properties", {})
        required = params.get("required", [])
        param_desc = ", ".join(f"{k}({v.get('type', 'any')})" for k, v in props.items())
        req_mark = "必填" if required else "可选"
        lines.append(f"- {name}: {desc} | 参数: {param_desc} | {req_mark}")
    return "\n".join(lines)


def _format_pending(pending: list[dict[str, Any]]) -> str:
    """格式化待确认 L2 操作列表为 LLM 可读摘要（M8-1，注入 tool_caller 上下文）。

    Args:
        pending: ``pending_confirmations`` 列表（safety_check 写入，含
            tool_name / tool_arguments 字段）。

    Returns:
        str: 每行一个待确认操作（工具名 + 参数），供 LLM 识别已挂起卡片。
    """
    lines = []
    for p in pending:
        name = p.get("tool_name", "")
        args = p.get("tool_arguments") or {}
        lines.append(f"- {name}({args})")
    return "\n".join(lines)


def _build_patient_context(state: AgentState) -> str | None:
    """构造当前接诊患者上下文（M8-5，注入 tool_caller 的 LLM 输入）。

    前端 ``context.patient_id`` 经 ``_build_initial_state`` 写入 AgentState 后，
    在此告知 LLM 当前患者 ID。B 端工具普遍必填 ``patient_id``
    （query_patient_history / check_drug_interaction 等 5 个），此前 LLM 上下文
    无"当前接诊患者"信息，联调时医生被反复追问患者 ID；注入后 LLM 直接携带该
    ID，不再索要。

    Args:
        state: 当前图状态，含 patient_id 字段。

    Returns:
        str | None: 患者上下文提示；``patient_id`` 为 None（前端未传入就诊患者）
            时不注入，避免把编造的 ID 塞给 LLM。
    """
    patient_id = state.get("patient_id")
    if patient_id is None:
        return None
    return (
        f"当前接诊患者 ID：{patient_id}（前端页面已选中的就诊患者）。"
        "涉及患者数据的工具必须携带此 patient_id，直接使用，不要向用户索要患者 ID。"
    )


def _build_hospital_context(state: AgentState) -> str | None:
    """构造当前医院上下文（注入 tool_caller 的 LLM 输入）。

    前端 ``context.hospital_id``（C 端页面"当前选择的医院"）经
    ``_resolve_hospital_id`` 解析后写入 AgentState.hospital_id。C 端 5 个工具
    （create_triage_assessment / query_departments / query_doctors /
    query_schedule_slots / create_appointment）将 hospital_id 标为必填，
    此前 LLM 上下文无"当前医院"信息：用户消息没提医院名时，LLM 只能反问
    "请问您在哪家医院"（体验差），或编造医院 ID。注入后 LLM 直接携带该 ID，
    不再索要、不编造。

    Args:
        state: 当前图状态，含 hospital_id 字段。

    Returns:
        str | None: 医院上下文提示；hospital_id 为 None（未解析到医院，
            如 C 端用户未选医院且 JWT 无 hospitalId）时不注入，避免把编造
            的 ID 塞给 LLM。
    """
    hospital_id = state.get("hospital_id")
    if hospital_id is None:
        return None
    return (
        f"当前医院 ID：{hospital_id}。涉及医院维度的工具（查科室/查医生/查排班/"
        "创建挂号/导诊分诊等）必须携带此 hospital_id，直接使用，不要向用户索要医院 ID。"
    )


def _build_department_context() -> str:
    """构造项目固定科室清单上下文（注入 tool_caller 的 LLM 输入）。

    项目现有科室固定为 ``DEPARTMENT_LIST``（17 个）。C 端工具 query_departments
    的 keyword 参数若用清单外的别名（如"心内科"/"消化科"），keyword 查询查不到
    科室（医院维度 LIKE 匹配）。此提示告知 LLM 可用科室名称，推荐/映射科室时
    必须从清单中选择，禁止编造清单外的名称。

    Returns:
        str: 科室清单约束提示。
    """
    names = "、".join(DEPARTMENT_LIST)
    return (
        f"当前项目可用的科室（仅以下 {len(DEPARTMENT_LIST)} 个，医院维度）：{names}。"
        "涉及科室的工具（query_departments 的 keyword）必须使用以上名称或其完整子串，"
        "禁止使用别名（如'心内科'→'心血管内科'、'消化科'→'消化内科'）或清单外的科室名。"
    )


def _build_address_context(state: AgentState) -> str | None:
    """构造当前收货地址上下文（注入 tool_caller 的 LLM 输入）。

    对齐原始需求 §3 药店推荐：C 端页面"当前配送地址"经 ``context.address_id``
    写入 AgentState.address_id。recommend_pharmacies 工具将 address_id 标为必填，
    此前 LLM 上下文无"当前收货地址"信息：用户消息没提地址时，LLM 只能反问
    "您要送到哪里"（体验差），或编造地址 ID。注入后 LLM 直接携带该 ID，
    不再索要、不编造。

    Args:
        state: 当前图状态，含 address_id 字段。

    Returns:
        str | None: 地址上下文提示；address_id 为 None（前端未选中配送地址）
            时不注入，避免把编造的 ID 塞给 LLM。
    """
    address_id = state.get("address_id")
    if address_id is None:
        return None
    return (
        f"当前收货地址 ID：{address_id}（前端页面已选中的配送地址）。"
        "涉及配送/药店推荐的工具（recommend_pharmacies）必须携带此 address_id，"
        "直接使用，不要向用户索要地址 ID。"
    )


def _fill_missing_required_param(
    tool_calls: list[dict[str, Any]], param_name: str, param_value: int | None
) -> list[dict[str, Any]]:
    """自动补全必填工具参数（确定性兜底，DRY 通用实现）。

    对"工具 schema 将 ``param_name`` 标为必填且 LLM 未填"的调用，从状态值
    ``param_value`` 确定性补全。LLM 提示词约束不可靠（可能漏填或编造），确定性
    补全杜绝 Java 侧必填参数缺失报错 / 用户或医生被反复索要本已掌握的信息。
    ``param_value`` 为 None 时不补全（不编造）。

    Args:
        tool_calls: LLM 提取出的工具调用列表（[{name, arguments}]）。
        param_name: 要补全的参数名（如 patient_id / hospital_id）。
        param_value: AgentState 中对应的参数值；None 时不补全。

    Returns:
        list[dict]: 补全后的新列表（不可变，原列表不修改）。
    """
    if param_value is None:
        return tool_calls
    filled: list[dict[str, Any]] = []
    for call in tool_calls:
        args = call.get("arguments") or {}
        tool = ToolRegistry.get_tool(call["name"])
        requires_param = bool(
            tool is not None and param_name in tool.parameters.get("required", [])
        )
        if requires_param and param_name not in args:
            filled.append({**call, "arguments": {**args, param_name: param_value}})
        else:
            filled.append(call)
    return filled


def _fill_missing_patient_id(
    tool_calls: list[dict[str, Any]], patient_id: int | None
) -> list[dict[str, Any]]:
    """自动补全必填 patient_id 工具参数（M8-5 确定性兜底）。

    B 端 5 个工具（query_patient_history / check_drug_interaction /
    check_contraindication / check_allergy_risk / check_duplicate_medication）的
    schema 将 patient_id 标为必填；C 端 patient_id 选填（默认本人）的工具不触碰。
    详见 :func:`_fill_missing_required_param`。
    """
    return _fill_missing_required_param(tool_calls, "patient_id", patient_id)


def _fill_missing_hospital_id(
    tool_calls: list[dict[str, Any]], hospital_id: int | None
) -> list[dict[str, Any]]:
    """自动补全必填 hospital_id 工具参数（C 端确定性兜底）。

    C 端 5 个工具（create_triage_assessment / query_departments /
    query_doctors / query_schedule_slots / create_appointment）的 schema 将
    hospital_id 标为必填。LLM 提示词约束不可靠（可能漏填或编造），此处对
    "工具 schema 必填 hospital_id 且 LLM 未填"的调用从 ``state.hospital_id``
    （前端 context.hospital_id，B 端为 JWT 医生所属医院）确定性补全，杜绝
    Java 侧 hospital_id 缺失报错 / 用户被反复索要医院。hospital_id 为 None
    时不补全（不编造）。详见 :func:`_fill_missing_required_param`。
    """
    return _fill_missing_required_param(tool_calls, "hospital_id", hospital_id)


def _fill_missing_address_id(
    tool_calls: list[dict[str, Any]], address_id: int | None
) -> list[dict[str, Any]]:
    """自动补全必填 address_id 工具参数（药店推荐确定性兜底）。

    对齐原始需求 §3：recommend_pharmacies 的 schema 将 address_id 标为必填。
    LLM 提示词约束不可靠（可能漏填或编造），此处对"工具 schema 必填 address_id
    且 LLM 未填"的调用从 ``state.address_id``（前端 context.address_id）确定性
    补全。address_id 为 None 时不补全（不编造）。详见
    :func:`_fill_missing_required_param`。
    """
    return _fill_missing_required_param(tool_calls, "address_id", address_id)


async def tool_caller(
    state: AgentState,
    allowed_tools: list[str] | None = None,
    scene_prompt: str | None = None,
) -> dict[str, Any]:
    """LLM 决定调用工具，返回 ``{"tool_calls": [...]}``。

    从 ``ToolRegistry`` 取当前 scope 的 L1/L2 工具 Schema，绑定到 LLM，
    解析返回的 ``tool_calls`` 写入状态。安全校验在此完成：L3/L4 工具被过滤
    （L2 由 safety_check 拦截生成确认）。

    子图分化（系分 §5.2.1）：``allowed_tools`` 为业务子图注入的工具白名单，
    非 None 时只绑定白名单内的 L1/L2 工具，让导诊/挂号/问诊/购药各子图
    只暴露本场景工具，减少 LLM 误选其他业务创建型操作。

    ⚠️ 白名单仅约束 C 端：四个业务子图的白名单（如 ``TRIAGE_TOOLS``）是 C 端
    患者场景专属，不含任何 B 端工具名。若对 B 端 scope 也按白名单过滤，B 端
    医生流程的全部工具会被滤空（``tool_calls=[]``，LLM 无工具可调）。因此
    B 端保持全量绑定当前 scope 的 L1/L2 工具（M5 验收"B 端 4 场景跑通"依赖此）。

    M8-4 提示词分 scope：系统提示词按 ``tool_scope`` 分支（C 端患者语义 /
    B 端医生工作台语义——医生身份、患者隐私脱敏、病历草稿不可签名等边界约束），
    与 M8-3 B 端直达工具子图配套，避免 B 端医生被 C 端患者语义误导。

    Args:
        state: 当前图状态，包含 scope / messages 字段。
        allowed_tools: 子图工具白名单（工具名列表）；None 表示不限（默认全量）。
            仅 C 端生效，B 端忽略该参数。
        scene_prompt: 场景专属指令（系分 §5.11 场景指令），业务子图传入本场景流程
            指令（如问诊场景的预问诊采集/处方解读要求）。作为 system 消息紧跟
            主提示词注入，引导 LLM 按场景流程推进。None 时不注入（通用场景零侵入，
            B 端与其他未传场景的子图无影响）。

    Returns:
        dict: 部分状态更新，包含 tool_calls（LLM 选择的 L1/L2 工具列表，
            格式为 ``[{"name": ..., "arguments": {...}}, ...]``）。

    Raises:
        无：LLM 调用失败时降级为 ``tool_calls=[]``，由回复节点兜底。
    """
    scope = state.get("scope", "c_end")
    try:
        tool_scope = ToolScope(scope)
    except ValueError:
        logger.warning("未知 scope=%s，默认使用 c_end 工具集", scope)
        tool_scope = ToolScope.C_END

    # 取 L1+L2 工具（L3/L4 不注册；L2 由 safety_check 拦截生成确认），转 OpenAI schema
    subgraph_tools = ToolRegistry.get_tools_by_scope(tool_scope)
    # 白名单仅约束 C 端：业务子图白名单是 C 端场景专属，B 端忽略白名单，
    # 否则 B 端工具（query_patient_history 等 8 个）会被全部滤空不可达
    effective_allowed = allowed_tools if tool_scope == ToolScope.C_END else None
    if effective_allowed is not None:
        allowed_set = set(effective_allowed)
        subgraph_tools = [t for t in subgraph_tools if t.name in allowed_set]
    tools = [
        t.to_openai_schema()
        for t in subgraph_tools
        if t.security_level in (SecurityLevel.L1, SecurityLevel.L2)
    ]
    if not tools:
        logger.warning("scope=%s 无可用 L1/L2 工具", scope)
        return {"tool_calls": []}

    llm = build_llm()
    llm_with_tools = llm.bind_tools(tools)

    history = truncate_messages(state.get("messages", []), get_settings().memory_window_size)
    # M8-4：系统提示词按 scope 分支——C 端用患者语义提示词，B 端用医生工作台
    # 提示词（医生身份/患者隐私/草稿边界），避免 B 端医生被 C 端"用户请求即授权"
    # 的患者语义误导（如草稿不可签名、患者数据脱敏等约束缺失）
    prompt_template = (
        B_TOOL_CALLER_SYSTEM_PROMPT if tool_scope == ToolScope.B_END else TOOL_CALLER_SYSTEM_PROMPT
    )
    system_prompt = prompt_template.format(
        tools_desc=_build_tools_prompt(tools), today=date.today().isoformat()
    )
    messages: list[dict[str, Any]] = [{"role": "system", "content": system_prompt}]
    # 场景提示词注入（系分 §5.11 场景指令）：业务子图传入本场景专属流程指令
    # （如问诊场景的预问诊采集/处方解读要求），作为 system 消息紧跟主提示词，
    # 引导 LLM 按场景流程推进。None 时不注入（通用场景零侵入）。
    if scene_prompt:
        messages.append({"role": "system", "content": scene_prompt})
    messages += history

    # M8-5：注入当前接诊患者上下文（前端 context.patient_id 非空时），
    # LLM 直接携带该 ID，避免 B 端必填 patient_id 工具反复向医生索要患者 ID。
    # 独立 system 消息而非拼进主提示词——无患者时零侵入，有患者时显式可见。
    patient_ctx = _build_patient_context(state)
    if patient_ctx:
        messages.append({"role": "system", "content": patient_ctx})

    # 注入当前医院上下文（AgentState.hospital_id 非空时）：C 端 5 个工具必填
    # hospital_id，LLM 直接携带该 ID，避免用户没提医院名时 LLM 反问或编造。
    # 与 patient_id 同策略——hospital_id 为 None 时不注入（不编造医院 ID）。
    hospital_ctx = _build_hospital_context(state)
    if hospital_ctx:
        messages.append({"role": "system", "content": hospital_ctx})

    # 注入当前收货地址上下文（AgentState.address_id 非空时）：recommend_pharmacies
    # 工具必填 address_id，LLM 直接携带该 ID，避免用户没选地址时 LLM 反问或编造。
    # 与 hospital_id 同策略——address_id 为 None 时不注入（不编造地址 ID）。
    address_ctx = _build_address_context(state)
    if address_ctx:
        messages.append({"role": "system", "content": address_ctx})

    # C 端注入项目固定科室清单（B 端医生工作台科室由 Java 端管理，不适用 C 端
    # 17 科室清单）：约束 query_departments 的 keyword 只能用清单内科室名，
    # 避免 LLM 用别名（心内科/消化科等）查不到科室。
    if tool_scope == ToolScope.C_END:
        messages.append({"role": "system", "content": _build_department_context()})

    # 注入已执行工具的结果（子图循环累积了前面所有轮次，LLM 分步决策可见）
    tool_results = state.get("tool_results")
    if tool_results:
        from app.orchestrator.nodes.reply import _format_tool_results

        summary = _format_tool_results(tool_results)
        messages.append({"role": "system", "content": f"已执行的工具结果：\n{summary}"})

    # M8-1：注入待确认 L2 摘要，让 LLM 知已有卡片，避免重复调用同一 L2
    # （软约束，硬兜底由 _dedupe_tool_calls 对比 pending + safety_check 复用 token）
    pending_confirmations = state.get("pending_confirmations")
    if pending_confirmations:
        messages.append(
            {
                "role": "system",
                "content": "以下操作正在等待用户确认，不要重复调用：\n"
                + _format_pending(pending_confirmations),
            }
        )

    try:
        response = await llm_with_tools.ainvoke(messages)
        tool_calls = _extract_tool_calls(response, tool_scope, effective_allowed)
        # M8-5：必填 patient_id / hospital_id 工具漏填时确定性补全（放在去重前——
        # 补全后的参数才是实际执行参数，去重按补全后对比，避免"轮 2 漏填未被去重、
        # 补全后与轮 1 实际执行参数相同仍被执行"的重复调用）。patient_id 兜底 B 端
        # 5 工具；hospital_id 兜底 C 端 5 工具（schema 驱动，非必填工具不触碰）。
        tool_calls = _fill_missing_patient_id(tool_calls, state.get("patient_id"))
        tool_calls = _fill_missing_hospital_id(tool_calls, state.get("hospital_id"))
        # 对齐原始需求 §3：recommend_pharmacies 必填 address_id，漏填时从
        # state.address_id（前端 context.address_id）确定性补全（不编造）。
        tool_calls = _fill_missing_address_id(tool_calls, state.get("address_id"))
        # 软兜底：拦截「相同参数 + 上次已成功」的重复调用（LLM 提示词收敛不可靠，
        # 这里做确定性去重——循环问题 P3-6）。意外截断/失败的重试放行，操作型 L2
        # 不进入 tool_results 天然豁免。M8-1：同时对比 pending_confirmations，
        # 源头拦截子图循环第二轮重复返回同一 L2（防两张 token 互异的确认卡）。
        tool_calls = _dedupe_tool_calls(
            tool_calls,
            state.get("tool_results") or [],
            state.get("pending_confirmations") or [],
        )
        logger.info(
            "工具决策: scope=%s, 选择 %d 个工具: %s",
            scope,
            len(tool_calls),
            [tc["name"] for tc in tool_calls],
        )
        iteration = state.get("tool_iteration") or 0
        return {"tool_calls": tool_calls, "tool_iteration": iteration + 1}
    except Exception as e:
        logger.error("工具决策失败: %s", e)
        return {"tool_calls": []}


def _dedupe_tool_calls(
    tool_calls: list[dict[str, Any]],
    executed: list[dict[str, Any]],
    pending: list[dict[str, Any]] | None = None,
) -> list[dict[str, Any]]:
    """过滤重复工具调用（确定性防循环 P3-6 + 防重复挂起 M8-1）。

    两类「重复」判定：
    1. 与已执行结果重复（L1 防循环）：工具名相同 + 参数完全一致 + 上次执行已成功
       - 上次失败/超时（success=False）→ 模型自主重试合理，放行
       - 参数不同 → 合法多步推进（换科室/换日期），放行
    2. 与待确认 L2 重复（M8-1 防重复挂起）：工具名相同 + 参数完全一致且
       已在 ``pending_confirmations`` 中 → 剔除，不重复发起同一 L2
       （避免子图循环第二轮 LLM 重复返回同一 L2，safety_check 又生成新
       token，导致两张 token 互异的确认卡 → 用户双确认 = 同一业务执行两次）

    Args:
        tool_calls: LLM 本轮要调用的工具列表（[{name, arguments}]）。
        executed: 本轮对话子图循环已执行的工具结果列表（含 tool_name /
            arguments / success 字段）。
        pending: 待用户确认的 L2 操作列表（safety_check 写入，含 tool_name /
            tool_arguments 字段）。None 或空时不做 L2 去重。

    Returns:
        list[dict]: 过滤后的工具调用列表，重复项被剔除。
    """
    if not executed and not pending:
        return tool_calls
    pending = pending or []
    deduped: list[dict[str, Any]] = []
    for call in tool_calls:
        name = call["name"]
        args = call.get("arguments") or {}
        # L1 防循环：与已执行成功结果重复 → 剔除
        is_executed_dup = any(
            prev.get("tool_name") == name
            and (prev.get("arguments") or {}) == args
            and prev.get("success")
            for prev in executed
        )
        # M8-1 防 L2 重复挂起：相同 tool_name+args 已在 pending → 剔除
        # （pending 项参数键为 tool_arguments，与 executed 的 arguments 区分）
        is_pending_dup = any(
            p.get("tool_name") == name and (p.get("tool_arguments") or {}) == args for p in pending
        )
        if is_executed_dup:
            logger.info("去重重复工具调用: %s %s（上次已成功）", name, args)
        elif is_pending_dup:
            logger.info("去重重复 L2 挂起: %s %s（已在 pending_confirmations）", name, args)
        else:
            deduped.append(call)
    return deduped


def _extract_tool_calls(
    response: Any,
    tool_scope: ToolScope,
    allowed_tools: list[str] | None = None,
) -> list[dict[str, Any]]:
    """从 LLM 响应中提取并过滤 tool_calls（只放行 L1/L2）。

    Args:
        response: LLM ainvoke 返回值（含 tool_calls 属性）。
        tool_scope: 当前服务端，用于校验工具是否存在。
        allowed_tools: 子图工具白名单；非 None 时只放行白名单内工具。
            仅 C 端传入（B 端由调用方置 None，白名单不约束 B 端）。

    Returns:
        list[dict]: 过滤后的 L1/L2 工具调用列表（L3/L4 拦截）。
    """
    calls = getattr(response, "tool_calls", None) or []
    allowed_set = set(allowed_tools) if allowed_tools is not None else None
    result: list[dict[str, Any]] = []
    for call in calls:
        # 兼容对象（langchain ToolCall）与 dict 两种格式（不同 LLM 返回不同）
        if isinstance(call, dict):
            name = call.get("name", "")
            args = call.get("args", {}) or {}
        else:
            name = getattr(call, "name", "")
            args = getattr(call, "args", {}) or {}
        tool = ToolRegistry.get_tool(name)
        # 双重过滤：工具必须存在且为 L1/L2（即便 LLM 返回 L3/L4 也拦截），
        # 且若子图限定了白名单，只放行白名单内的工具
        is_allowed = (
            tool is not None
            and tool.scope == tool_scope
            and tool.security_level in (SecurityLevel.L1, SecurityLevel.L2)
            and (allowed_set is None or name in allowed_set)
        )
        if is_allowed:
            result.append({"name": name, "arguments": args})
        else:
            logger.warning("过滤非 L1/L2 工具调用: %s", name)
    return result
