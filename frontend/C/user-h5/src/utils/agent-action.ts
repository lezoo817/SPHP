import type { AgentActionCard, AgentChatContext } from '../typings/agent';

/** 受控业务交互卡解析后的固定发送请求。 */
export interface AgentActionRequest {
  /** 发送给 Agent 的固定用户提示。 */
  content: string;
  /** 不可信卡片参数校验后生成的对话上下文。 */
  context: AgentChatContext;
}

/**
 * 将业务交互卡转换为受控 Agent 预设请求。
 *
 * @param card SSE 返回的业务交互卡
 * @param context 当前 AI 页面上下文
 * @returns 仅包含合法业务 ID 的固定请求；未知动作或非法参数返回 undefined
 */
export function resolveAgentActionRequest(
  card: Pick<AgentActionCard, 'actionType' | 'arguments'>,
  context?: AgentChatContext,
): AgentActionRequest | undefined {
  if (card.actionType === 'recommend_prescription_pharmacy') {
    const prescriptionId = Number(card.arguments.prescription_id);
    if (!Number.isInteger(prescriptionId) || prescriptionId <= 0) return undefined;
    return {
      content: '请为我推荐相关药店。',
      context: {
        ...context,
        preset_action: 'recommend_prescription_pharmacy',
        prescription_id: prescriptionId,
      },
    };
  }
  if (card.actionType === 'authorize_drug_order_reminder_after_receipt') {
    const drugOrderId = Number(card.arguments.drug_order_id);
    if (!Number.isInteger(drugOrderId) || drugOrderId <= 0) return undefined;
    return {
      content: '请设置本订单收货后自动开启用药提醒。',
      context: {
        ...context,
        preset_action: 'authorize_drug_order_reminder_after_receipt',
        drug_order_id: drugOrderId,
      },
    };
  }
  return undefined;
}
