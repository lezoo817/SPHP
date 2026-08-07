/**
 * AI 助手常量：Agent 服务地址、错误码映射、工具标签、欢迎语与快捷入口。
 *
 * Agent 服务地址由运行配置注入；开发环境默认端口 8081。
 */

/** Agent 服务基础地址（开发环境默认 8081）。 */
export const AGENT_BASE_URL =
  (typeof window !== 'undefined' && (window as any).__AGENT_BASE_URL__) || 'http://localhost:8081';

/** C 端对话固定 scope。 */
export const AGENT_SCOPE = 'c_end' as const;

/** 用户输入最大长度（与后端一致）。 */
export const AGENT_CONTENT_MAX = 2000;

/** 会话存储键名。 */
export const AGENT_SESSION_KEY = 'sphp_c_agent_session_id';

/** 欢迎语。 */
export const AGENT_WELCOME =
  '您好，我是智愈先锋 AI 助手，可以帮您智能导诊、挂号、问诊、购药和查阅健康档案。请问有什么可以帮您？';

/** 常用咨询快捷入口（常驻在对话列表上方，每个按钮含图标 + 标题 + 描述）。 */
export interface AgentQuickPrompt {
  label: string;
  content: string;
  description: string;
  /** 需要固定发起的记录选择预设。 */
  pickerAction?: 'select_prescription_interpretation' | 'select_medical_record_interpretation';
}

export const AGENT_QUICK_PROMPTS: AgentQuickPrompt[] = [
  { label: '智能导诊', content: '开启智能导诊服务', description: '描述症状，推荐科室' },
  { label: '查询挂号', content: '帮我查一下当前的挂号订单。', description: '查看预约与就诊流程' },
  { label: '处方解读', content: '请选择最近的处方供我解读。', description: '选择处方后通俗解释', pickerAction: 'select_prescription_interpretation' },
  { label: '病历解读', content: '请选择最近的病历供我解读。', description: '选择病历后通俗解读', pickerAction: 'select_medical_record_interpretation' },
  { label: '在线问诊', content: '我想进行在线问诊', description: '找医生，提交预问诊' },
  { label: '健康档案', content: '查看我的健康档案和过敏史。', description: '过敏史 / 既往史 / 用药' },
];

/** 工具英文标识符到中文标签的映射（与 sphp-agent `_TOOL_LABELS` 对齐）。 */
export const AGENT_TOOL_LABELS: Record<string, string> = {
  create_appointment: '确认挂号',
  cancel_appointment: '确认取消挂号',
  save_pre_consultation: '确认提交预问诊',
  send_consultation_message: '确认发送问诊消息',
  create_drug_order: '确认创建购药订单',
  authorize_drug_order_reminder_after_receipt: '确认收货后开启用药提醒',
  cancel_drug_order: '确认取消购药订单',
  confirm_drug_receipt: '确认收货',
  manage_allergy: '确认更新过敏史',
  manage_medical_history: '确认更新既往史',
  create_report: '确认录入检查报告',
  update_medication_plan: '确认更新用药计划',
  confirm_follow_up: '确认随访提醒',
  join_waitlist: '确认登记候补',
  generate_draft_note: '确认保存病历草稿',
  query_patient_history: '确认查询患者档案',
  query_hospitals: '查询医院',
  query_departments: '查询科室',
  query_doctors: '查询医生',
  query_doctor_slots: '查询号源',
  triage_assessment: '症状导诊',
  query_appointments: '查询挂号',
  query_appointment_detail: '查询挂号详情',
  query_consultations: '查询问诊',
  query_consultation_detail: '查询问诊详情',
  query_prescriptions: '查询处方',
  query_medical_records: '查询病历',
  query_prescription_detail: '查询处方详情',
  query_prescription_interpretation: '处方解读',
  interpret_medical_record: '病历解读',
  query_pharmacy_inventory: '查询药房库存',
  query_drug_orders: '查询购药订单',
  query_drug_order_detail: '查询购药订单详情',
  query_health_record: '查询健康档案',
  query_reports: '查询检查报告',
  query_report_detail: '查询报告详情',
  query_report_interpretation: '报告解读',
  query_medication_plans: '查询用药计划',
  query_follow_ups: '查询随访计划',
  query_notifications: '查询通知',
  query_profile: '查询本人资料',
  query_family_members: '查询家庭成员',
  query_payment: '查询支付状态',
};

/** L2 卡片类型到对应工具名的反向映射（与 sphp-agent safety.py `_map_card_type` 对齐）。
 * 前端收到 `card` 事件后，据此把对应 L2 工具的 loading 卡片收尾，
 * 因其不下发 observation（被挂起 pending_confirmations），否则会永久显示"调用中"。 */
export const AGENT_CARD_TYPE_TO_TOOL: Record<string, string> = {
  confirm_appointment: 'create_appointment',
  confirm_cancel_appointment: 'cancel_appointment',
  confirm_pre_consultation: 'save_pre_consultation',
  confirm_send_message: 'send_consultation_message',
  confirm_drug_order: 'create_drug_order',
  confirm_cancel_drug_order: 'cancel_drug_order',
  confirm_drug_receipt: 'confirm_drug_receipt',
  confirm_waitlist: 'join_waitlist',
  confirm_allergy: 'manage_allergy',
  confirm_medical_history: 'manage_medical_history',
  confirm_report: 'create_report',
  confirm_medication_plan: 'update_medication_plan',
  confirm_drug_order_reminder_after_receipt: 'authorize_drug_order_reminder_after_receipt',
  confirm_follow_up: 'confirm_follow_up',
  confirm_draft_note: 'generate_draft_note',
  confirm_patient_history: 'query_patient_history',
};

/** L2 确认卡片错误码到面向患者的提示文案映射（系分 §9.4 / §11）。 */
export const AGENT_CONFIRM_ERROR_TEXT: Record<string, string> = {
  CONFIRM_INVALID: '确认参数无效，请重新发起操作',
  CONFIRM_CONSUMED: '已处理，无需重复确认',
  CONFIRM_EXPIRED: '确认已超时，请重新发起操作',
  SESSION_MISMATCH: '会话不匹配，请重新发起操作',
  TOOL_FAILED: '操作执行失败，请稍后重试',
  TOOL_DENIED: '该操作已被安全策略阻止',
};

/** Agent / SSE 错误码到面向患者提示的映射（系分 §11）。 */
export const AGENT_ERROR_TEXT: Record<string, string> = {
  AUTH_MISSING: '未登录，请重新登录',
  AUTH_EXPIRED: '登录已失效，请重新登录',
  AUTH_INVALID: '登录已失效，请重新登录',
  RATE_LIMITED: '对话请求过于频繁，请稍后重试',
  INVALID_REQUEST: '请求参数无效，请修改后重试',
  SESSION_NOT_FOUND: '会话已过期，已为您创建新会话',
  TOOL_DENIED: '该操作暂不支持',
  TOOL_FAILED: '服务暂时不可用，请稍后重试',
  SERVER_ERROR: '服务异常，请稍后重试',
  NETWORK_ERROR: '网络连接失败，请检查网络后重试',
  STREAM_ERROR: '对话连接中断，请重试',
};

/** 医疗免责声明（导诊、报告、处方解读与 AI 回复均展示）。 */
export const AGENT_DISCLAIMER = '本结果仅供健康咨询参考，不替代医生诊断，如有不适请及时就医。';
