/**
 * AI 助手会话状态：当前 sessionId 与对话上下文快照。
 *
 * 与现有 models 模块一致，使用 sessionStorage 持久化最小状态；
 * 消息列表、工具卡片等 UI 状态由 useAgentStream 在内存中维护。
 */
import { getSelection } from './selection';
import type { AgentChatContext } from '../typings/agent';

/**
 * 读取当前 Agent 会话 ID。
 * @returns 会话内保存的会话 ID；不存在时返回 undefined
 */
export function getAgentSessionId(): string | undefined {
  if (typeof window === 'undefined') return undefined;
  return window.sessionStorage.getItem('sphp_c_agent_session_id') || undefined;
}

/**
 * 保存 Agent 会话 ID，供后续对话继续同一会话。
 * @param sessionId Agent 在 done 事件中返回的会话 ID
 */
export function saveAgentSessionId(sessionId: string): void {
  if (typeof window !== 'undefined' && sessionId) {
    window.sessionStorage.setItem('sphp_c_agent_session_id', sessionId);
  }
}

/** 清除当前 Agent 会话 ID（会话不存在或过期时调用）。 */
export function clearAgentSessionId(): void {
  if (typeof window !== 'undefined') window.sessionStorage.removeItem('sphp_c_agent_session_id');
}

/**
 * 基于当前页面路径和跨页面选择构造对话上下文。
 * @param pathname 当前路由路径
 * @returns 对话上下文，含页面标识与已选医院、就诊人
 */
export function buildAgentContext(pathname: string): AgentChatContext {
  let page: AgentChatContext['page'];
  if (pathname.startsWith('/assistant')) page = 'consultation';
  else if (pathname.startsWith('/pharmacy')) page = 'pharmacy';
  else if (pathname.startsWith('/mine')) page = 'health';
  else if (pathname.startsWith('/home')) page = 'triage';
  else page = 'triage';

  const selection = getSelection();
  const context: AgentChatContext = { page };
  if (selection.hospitalId) context.hospital_id = selection.hospitalId;
  if (selection.patientId) context.patient_id = selection.patientId;

  // 路径参数补充：挂号详情 / 问诊详情携带对应 ID
  const appointmentMatch = pathname.match(/\/assistant\/(?:book|pay|pre-consultation)\/(\d+)/);
  if (appointmentMatch) context.appointment_id = Number(appointmentMatch[1]);
  const consultationMatch = pathname.match(/\/assistant\/consultation\/(\d+)/);
  if (consultationMatch) context.consultation_id = Number(consultationMatch[1]);

  return context;
}
