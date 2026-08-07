import type { DrugOrderAgentReturnState } from '../typings/agent';

/**
 * 从 Agent 下单确认结果中提取订单和支付单 ID。
 * @param actionResult Agent 确认接口返回的原始业务结果。
 * @returns 可用于进入支付页的订单信息；字段非法时返回 undefined。
 */
export function resolveDrugOrderPaymentResult(
  actionResult: unknown,
): { drugOrderId: number; paymentId: number } | undefined {
  if (!actionResult || typeof actionResult !== 'object') return undefined;
  const response = actionResult as Record<string, unknown>;
  // MCP 确认接口保留 Java 统一信封；兼容未来已解包的 action_result。
  const result = response.data && typeof response.data === 'object'
    ? response.data as Record<string, unknown>
    : response;
  const drugOrderId = Number(result.drugOrderId);
  const paymentId = Number(result.paymentId);
  if (!Number.isInteger(drugOrderId) || drugOrderId <= 0) return undefined;
  if (!Number.isInteger(paymentId) || paymentId <= 0) return undefined;
  return { drugOrderId, paymentId };
}

/**
 * 校验支付页路由状态中的 AI 会话返回信息。
 * @param value 未受信任的路由状态值。
 * @returns 合法的最小会话恢复信息；非法值返回 undefined。
 */
export function resolveDrugOrderAgentReturnState(value: unknown): DrugOrderAgentReturnState | undefined {
  if (!value || typeof value !== 'object') return undefined;
  const state = value as Partial<DrugOrderAgentReturnState>;
  if (!state.sessionId || !state.sessionId.trim() || !state.from || !state.from.startsWith('/')) return undefined;
  return { sessionId: state.sessionId, from: state.from };
}
