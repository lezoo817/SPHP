/**
 * B 端 AI 辅助面板常量：Agent 服务地址、scope、工具标签、错误码映射、欢迎语与快捷入口。
 *
 * Agent 服务独立部署于 :8081 端口，B 端通过 SSE 流式对话接入。
 */

/** Agent 服务基础地址（开发环境默认 8081）。 */
export const AGENT_BASE_URL =
  (typeof window !== 'undefined' && (window as any).__AGENT_BASE_URL__) || 'http://localhost:8081';

/** B 端对话固定 scope。 */
export const AGENT_SCOPE = 'b_end' as const;

/** 用户输入最大长度（与后端一致）。 */
export const AGENT_CONTENT_MAX = 2000;

/** 会话存储键名（按"一次问诊一个会话"策略维护，切换患者时清除）。 */
export const AGENT_SESSION_KEY = 'sphp_b_agent_session_id';

/** 欢迎语（B 端面向医生）。 */
export const AGENT_WELCOME =
  '您好，我是智愈先锋 AI 辅助助手，可帮您查询患者档案、检索用药指南、检测药物相互作用、生成病历草稿、审核处方风险、解读检查报告。';

/** 接诊台常用快捷入口（B 端场景）。 */
export const AGENT_QUICK_PROMPTS: { label: string; content: string }[] = [
  { label: '生成病历草稿', content: '请根据当前接诊患者信息生成病历草稿' },
  { label: '用药相互作用', content: '请检测当前处方药品的相互作用' },
  { label: '患者档案', content: '查询当前患者的历史就诊记录与用药清单' },
  { label: '处方审核', content: '请审核当前处方的禁忌与过敏风险' },
  { label: '药品说明', content: '查询阿莫西林胶囊的用药指南' },
  { label: '报告解读', content: '请解读当前患者的检查报告指标' },
];

/**
 * B 端工具英文标识符到中文标签的映射。
 *
 * Agent 编排层在 SSE action 事件中下发 tool 名，前端按此映射展示中文标签。
 * 包含 C/B 共用工具与 B 端专属工具。
 */
export const AGENT_TOOL_LABELS: Record<string, string> = {
  // B 端接诊辅助
  query_patient_history: '查询患者档案',
  query_drug_guide: '查询用药指南',
  check_drug_interaction: '检测药物相互作用',
  generate_draft_note: '生成病历草稿',
  // B 端导诊推荐
  recommend_care: '推荐科室与医生',
  // B 端处方审核
  check_contraindication: '查询药品禁忌',
  check_allergy_risk: '查询过敏风险',
  check_duplicate_medication: '查询重复用药',
  // B 端报告解读（本地工具）
  interpret_report: '解读检查报告',
  // 兜底通用查询标签
  query_departments: '查询科室',
  query_doctors: '查询医生',
  query_schedules: '查询排班',
  query_drugs: '查询药品目录',
};

/** L2 确认卡片错误码到面向医生的提示文案映射。 */
export const AGENT_CONFIRM_ERROR_TEXT: Record<string, string> = {
  CONFIRM_INVALID: '确认参数无效，请重新发起操作',
  CONFIRM_CONSUMED: '已处理，无需重复确认',
  CONFIRM_EXPIRED: '确认已超时，请重新发起操作',
  SESSION_MISMATCH: '会话不匹配，请重新发起操作',
  TOOL_FAILED: '操作执行失败，请稍后重试',
  TOOL_DENIED: '该操作已被安全策略阻止',
};

/** Agent / SSE 错误码到面向医生的提示映射。 */
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
  NETWORK_ERROR: '网络连接失败，请稍后重试',
  STREAM_ERROR: '对话连接中断，请重试',
};

/** AI 辅助提示降级文案（SSE 连接失败或超时）。 */
export const AGENT_UNAVAILABLE_TEXT = 'AI 辅助暂不可用，不影响接诊、处方等核心操作';

/** 医疗免责声明（生成草稿、报告解读、处方审核等均展示）。 */
export const AGENT_DISCLAIMER = 'AI 辅助内容仅供医生参考，不替代临床判断与医生诊断。';
