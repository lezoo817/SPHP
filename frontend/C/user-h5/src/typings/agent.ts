/**
 * AI 助手（Agent）相关类型定义。
 *
 * 与 sphp-agent 的 `app/api/routes/chat.py` SSE 事件格式对齐，覆盖系分 §9
 * 的七类事件：message / thought / action / observation / card / error / done，
 * 以及 L2 确认回调的请求与响应结构。
 */

/** 对话上下文：描述当前页面业务状态，辅助 Agent 决策。 */
export interface AgentChatContext {
  /** 当前页面：triage、appointment、consultation、pharmacy、health */
  page?: 'triage' | 'appointment' | 'consultation' | 'pharmacy' | 'health';
  /** 当前选择医院 ID */
  hospital_id?: number;
  /** 当前选中医生 ID */
  doctor_id?: number;
  /** 当前就诊人 ID */
  patient_id?: number;
  /** 当前挂号订单 ID */
  appointment_id?: number;
  /** 当前问诊记录 ID */
  consultation_id?: number;
  /** 当前默认收货地址 ID（用于 Agent 推荐药店等需要收货地址的服务） */
  address_id?: number;
}

/** 发起流式对话的请求体（POST /api/chat/stream）。 */
export interface AgentChatRequest {
  /** 用户输入文本，1 至 2000 字 */
  content: string;
  /** 固定为 c_end */
  scope: 'c_end';
  /** 会话 ID；为空时 Agent 创建新会话 */
  session_id?: string;
  /** 附加上下文 */
  context?: AgentChatContext;
}

/** message 事件：流式文本增量。 */
export interface AgentMessageEvent {
  /** 本次增量文本片段，前端累加拼接 */
  delta: string;
}

/** thought 事件：推理模型的思考增量。 */
export interface AgentThoughtEvent {
  /** 推理增量文本片段，仅推理模型触发 */
  delta: string;
}

/** action 事件：工具调用开始。 */
export interface AgentActionEvent {
  /** 工具英文标识符，用于与 observation 配对 */
  tool: string;
  /** 传入工具的完整参数 */
  arguments: Record<string, unknown>;
}

/** observation 事件：工具调用结果。 */
export interface AgentObservationEvent {
  /** 工具英文标识符，与对应 action 配对 */
  tool: string;
  /** 执行状态：success 或 error（后端字段为 success 布尔） */
  status: 'success' | 'error';
  /** 工具完整返回数据（后端当前可能不返回，可选） */
  result?: unknown;
  /** 结果一行摘要（后端当前可能不返回，可选） */
  summary?: string;
  /** 工具执行耗时，单位毫秒（后端当前可能不返回，可选） */
  duration_ms?: number;
  /** 失败原因（后端在失败时下发 error 字符串） */
  error?: string;
}

/** L2 确认卡片类型，决定渲染样式和详情字段。 */
export type AgentCardType =
  | 'confirm_appointment'
  | 'confirm_cancel_appointment'
  | 'confirm_pre_consultation'
  | 'confirm_send_message'
  | 'confirm_drug_order'
  | 'confirm_cancel_drug_order'
  | 'confirm_allergy'
  | 'confirm_medical_history'
  | 'confirm_report'
  | 'confirm_medication_plan'
  | 'confirm_follow_up'
  | 'confirm_generic';

/** card 事件：L2 操作确认卡片。 */
export interface AgentCardEvent {
  /** 卡片类型 */
  card_type: AgentCardType;
  /** 确认令牌，确认时原样传回 */
  confirm_token: string;
  /** 当前会话 ID；确认请求必须与 confirm_token 一并传回 */
  session_id: string;
  /** 卡片标题 */
  title: string;
  /** 单行摘要 */
  summary: string;
  /** 结构化详情，字段随卡片类型变化（后端当前可能不返回） */
  details?: Record<string, unknown>;
  /** 令牌过期时间；到期后禁用确认 */
  expires_at?: string;
}

/** options 事件：可选项列表卡片（区别于"确认一个操作"的 L2 卡片）。
 *
 * 后端在多选项场景（如医生列表、科室列表、号源列表）下确定性下发，
 * 让前端以"单选点选"形式承载选择动作，避免纯文本让 LLM 配对 ID。
 */
export interface AgentOptionsEvent {
  /** 选项卡类型：select_doctor / select_department / select_slot / select_pharmacy */
  type: 'select_doctor' | 'select_department' | 'select_slot' | 'select_pharmacy' | string;
  /** 选项列表 */
  items: AgentSelectItem[];
  /** 引导用户选择的提示语 */
  prompt?: string;
  /** 用户选择后会发送的文本模板，{label} 占位为选项展示名，默认"我选择{label}" */
  reply_template?: string;
}

/** 单个可选项。 */
export interface AgentSelectItem {
  /** 选项 ID，与后端配对的字段 */
  id: string;
  /** 展示标题（用户点击的可见文案） */
  label: string;
  /** 副标题（科室 / 职称 / 等） */
  description?: string;
  /** 附加元数据（前端可选消费） */
  meta?: Record<string, unknown>;
}

/** 可选项卡片的运行时对象。 */
export interface AgentSelectCard {
  id: string;
  selectType: AgentOptionsEvent['type'];
  items: AgentSelectItem[];
  prompt?: string;
  /** 构造"我选择{label}"等消息所用的模板 */
  replyTemplate: string;
  /** 用户已选项的 ID（单选） */
  selectedId?: string;
  createdAt: number;
}

/** error 事件：对话或工具执行错误。 */
export interface AgentErrorEvent {
  /** Agent、MCP 或下游业务错误码 */
  code: string;
  /** 面向患者的错误说明 */
  message: string;
  /** 全链路追踪 ID */
  trace_id?: string;
}

/** done 事件：本轮结束。 */
export interface AgentDoneEvent {
  /** 当前会话 ID，前端用于后续请求 */
  session_id: string;
  /** 可选，本轮 Token 用量统计 */
  usage?: Record<string, number> | null;
  /** 可选，全链路追踪 ID */
  trace_id?: string;
}

/** 八类 SSE 事件的联合类型（message/thought/action/observation/card/options/error/done）。 */
export type AgentSseEvent =
  | { event: 'message'; data: AgentMessageEvent }
  | { event: 'thought'; data: AgentThoughtEvent }
  | { event: 'action'; data: AgentActionEvent }
  | { event: 'observation'; data: AgentObservationEvent }
  | { event: 'card'; data: AgentCardEvent }
  | { event: 'options'; data: AgentOptionsEvent }
  | { event: 'error'; data: AgentErrorEvent }
  | { event: 'done'; data: AgentDoneEvent };

/** L2 确认回调请求体（POST /api/chat/confirm）。 */
export interface AgentConfirmRequest {
  confirm_token: string;
  session_id: string;
}

/** L2 确认回调响应数据。 */
export interface AgentConfirmData {
  /** MCP 工具返回的业务执行结果 */
  action_result?: unknown;
  /** 面向患者的业务结果提示 */
  message?: string;
}

/** 确认卡片在 UI 中的运行时状态。 */
export type ConfirmCardStatus = 'pending' | 'confirming' | 'done' | 'error' | 'expired';

/** 会话消息类型。 */
export type AgentMessageRole = 'user' | 'assistant';

/** 一条会话消息（AI 文本累加或用户输入）。 */
export interface AgentMessage {
  /** 前端生成的稳定 ID */
  id: string;
  role: AgentMessageRole;
  /** 文本内容（AI 消息随 message.delta 累加） */
  content: string;
  /** 是否仍在本轮流式输出中 */
  streaming?: boolean;
  /** 创建时间戳 */
  createdAt: number;
}

/** 思考片段（thought.delta 累加）。 */
export interface AgentThought {
  id: string;
  content: string;
  streaming?: boolean;
  createdAt: number;
}

/** 工具调用卡片（action 与 observation 配对）。 */
export interface AgentToolCard {
  id: string;
  /** 工具英文标识符 */
  tool: string;
  /** 工具中文标签（前端按 tool 推导） */
  label: string;
  /** 传入工具的参数 */
  arguments?: Record<string, unknown>;
  /** 执行状态 */
  status: 'loading' | 'success' | 'error';
  /** 结果摘要 */
  summary?: string;
  /** 失败原因 */
  error?: string;
  /** 完整结果 */
  result?: unknown;
  /** 执行耗时（毫秒） */
  durationMs?: number;
  createdAt: number;
}

/** L2 确认卡片运行时对象。 */
export interface AgentConfirmCard {
  id: string;
  cardType: AgentCardType;
  confirmToken: string;
  sessionId: string;
  title: string;
  summary: string;
  details?: Record<string, unknown>;
  expiresAt?: string;
  status: ConfirmCardStatus;
  /** 确认成功后的业务结果提示 */
  resultMessage?: string;
  /** 确认失败时的错误码 */
  errorCode?: string;
  /** 确认失败时的错误说明 */
  errorMessage?: string;
  createdAt: number;
}

/** 会话条目类型：消息、思考、工具卡片、确认卡片、可选项卡片按到达顺序排列。 */
export type AgentEntry =
  | { kind: 'message'; data: AgentMessage }
  | { kind: 'thought'; data: AgentThought }
  | { kind: 'tool'; data: AgentToolCard }
  | { kind: 'card'; data: AgentConfirmCard }
  | { kind: 'select'; data: AgentSelectCard };

/** 流式连接状态。 */
export type AgentConnectionState = 'idle' | 'connecting' | 'streaming' | 'error';

/** 历史会话条目（GET /api/chat/sessions 响应）。 */
export interface AgentSession {
  /** 会话 ID */
  session_id: string;
  /** 会话标题（首条用户消息截断） */
  title: string;
  /** 最后一条助手消息预览 */
  last_message: string | null;
  /** 消息轮次 */
  message_count: number;
  /** 最后更新时间（ISO 8601） */
  updated_at: string;
}

/** 历史会话列表响应。 */
export interface AgentSessionList {
  sessions: AgentSession[];
}
