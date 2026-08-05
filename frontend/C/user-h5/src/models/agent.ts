/**
 * AI 助手会话状态：当前 sessionId 与对话上下文快照。
 *
 * 与现有 models 模块一致，使用 sessionStorage 持久化最小状态；
 * 消息列表、工具卡片等 UI 状态由 useAgentStream 在内存中维护。
 */
import { getSelection } from './selection';
import { getDeliveryAddresses } from '../services/delivery-address';
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
 * 基于当前页面路径和跨页面选择构造对话上下文（同步基础字段）。
 * 不包含地址等需要异步拉取的字段，请使用 resolveAgentContext。
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

/**
 * 异步加载当前账号默认收货地址 ID。
 *
 * 服务端默认地址优先返回；无默认地址时退回到首条有效地址。
 * 接口失败时返回 undefined，不影响其他上下文字段。
 * @returns 默认收货地址 ID；无地址或请求失败时返回 undefined
 */
export async function loadDefaultAddressId(): Promise<number | undefined> {
  try {
    const list = await getDeliveryAddresses();
    if (!Array.isArray(list) || list.length === 0) return undefined;
    return list.find((item) => item.isDefault)?.id ?? list[0].id;
  } catch {
    return undefined;
  }
}

/**
 * 异步构造完整对话上下文：基础字段 + 默认收货地址 ID。
 *
 * 调用方应在使用前主动 await。地址拉取失败时仅缺少 address_id，
 * 其他字段（hospital_id / patient_id / appointment_id / consultation_id）不受影响。
 * @param pathname 当前路由路径
 * @returns 含默认收货地址的完整对话上下文
 */
export async function resolveAgentContext(pathname: string): Promise<AgentChatContext> {
  const context = buildAgentContext(pathname);
  const addressId = await loadDefaultAddressId();
  if (addressId !== undefined) context.address_id = addressId;
  return context;
}

