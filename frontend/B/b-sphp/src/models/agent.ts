/**
 * B 端 AI 辅助面板会话状态：当前 sessionId 与对话上下文快照。
 *
 * 会话策略（Agent 模块系分 V2.1 §5.9 B 端会话管理）：
 * - 医生每次开始接诊对应一个独立的 Agent 会话；
 * - 切换患者（consultation_id 变化）时前端不传 session_id，Agent 创建新会话；
 * - 结束问诊时前端丢弃 session_id，旧会话按 TTL 自然过期。
 *
 * 使用 sessionStorage 持久化最小状态；消息列表、工具卡片等 UI 状态由
 * useAgentStream 在内存中维护。
 */
import { AGENT_SESSION_KEY } from '../constants/agent';
import type { AgentChatContext } from '../typings/agent';

/**
 * 读取当前 Agent 会话 ID。
 * @returns 会话内保存的会话 ID；不存在时返回 undefined
 */
export function getAgentSessionId(): string | undefined {
  if (typeof window === 'undefined') return undefined;
  return window.sessionStorage.getItem(AGENT_SESSION_KEY) || undefined;
}

/**
 * 保存 Agent 会话 ID，供后续对话继续同一会话。
 * @param sessionId Agent 在 done 事件中返回的会话 ID
 */
export function saveAgentSessionId(sessionId: string): void {
  if (typeof window !== 'undefined' && sessionId) {
    window.sessionStorage.setItem(AGENT_SESSION_KEY, sessionId);
  }
}

/** 清除当前 Agent 会话 ID（会话不存在、过期或切换患者时调用）。 */
export function clearAgentSessionId(): void {
  if (typeof window !== 'undefined') window.sessionStorage.removeItem(AGENT_SESSION_KEY);
}

/**
 * 基于当前页面路径和接诊上下文构造对话上下文。
 *
 * 接诊台详情页携带 consultation_id / patient_id（系分 §9.2 病历草稿生成的上下文参数），
 * 其他页面按业务域映射 page 标识。
 *
 * @param pathname 当前路由路径
 * @param options 可选的接诊上下文（consultationId / patientId）
 * @returns 对话上下文，含页面标识与当前接诊患者、问诊记录
 */
export function buildAgentContext(
  pathname: string,
  options: { consultationId?: number; patientId?: number; doctorId?: number; hospitalId?: number } = {},
): AgentChatContext {
  let page: AgentChatContext['page'];
  if (pathname.startsWith('/consult')) page = 'consultation';
  else if (pathname.startsWith('/prescription')) page = 'prescription';
  else if (pathname.startsWith('/drug')) page = 'pharmacy';
  else if (pathname.startsWith('/patient')) page = 'health';
  else if (pathname.startsWith('/schedule')) page = 'triage';
  else page = 'doctor_workbench';

  const context: AgentChatContext = { page };
  if (options.hospitalId) context.hospital_id = options.hospitalId;
  if (options.doctorId) context.doctor_id = options.doctorId;
  if (options.patientId) context.patient_id = options.patientId;
  if (options.consultationId) context.consultation_id = options.consultationId;

  // 路径参数补充：接诊详情页携带问诊 ID
  const consultMatch = pathname.match(/\/consult\/detail\/(\d+)/);
  if (consultMatch && !context.consultation_id) {
    context.consultation_id = Number(consultMatch[1]);
  }

  return context;
}
