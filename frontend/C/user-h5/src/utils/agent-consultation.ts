import type { ConsultationAgentReturnState } from '../typings/agent';

/**
 * 从 Agent 问诊确认结果中提取问诊记录 ID。
 *
 * @param actionResult Agent 确认接口返回的原始业务结果。
 * @param fallbackConsultationId 发送既有问诊消息时确认卡中保存的问诊记录 ID。
 * @returns 可用于进入在线问诊详情页的记录 ID；字段非法时返回 undefined。
 */
export function resolveConsultationId(
  actionResult: unknown,
  fallbackConsultationId?: unknown,
): number | undefined {
  const result = unwrapActionResult(actionResult);
  const consultationId = Number(result?.consultationId ?? fallbackConsultationId);
  return Number.isInteger(consultationId) && consultationId > 0 ? consultationId : undefined;
}

/**
 * 校验在线问诊页路由状态中的 AI 会话返回信息。
 *
 * @param value 未受信任的路由 state 值。
 * @returns 合法的最小会话恢复信息；非法值返回 undefined。
 */
export function resolveConsultationAgentReturnState(value: unknown): ConsultationAgentReturnState | undefined {
  if (!value || typeof value !== 'object') return undefined;
  const state = value as Partial<ConsultationAgentReturnState>;
  if (!state.sessionId || !state.sessionId.trim() || !state.from || !state.from.startsWith('/')) return undefined;
  return { sessionId: state.sessionId, from: state.from };
}

/**
 * 解开 Agent 保留的 Java 统一响应信封。
 *
 * @param actionResult Agent 确认接口返回的原始业务结果。
 * @returns 已解包的业务结果；格式异常时返回 undefined。
 */
function unwrapActionResult(actionResult: unknown): Record<string, unknown> | undefined {
  if (!actionResult || typeof actionResult !== 'object') return undefined;
  const response = actionResult as Record<string, unknown>;
  return response.data && typeof response.data === 'object'
    ? response.data as Record<string, unknown>
    : response;
}
