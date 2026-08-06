import type { Appointment } from '../typings/api';

/** 就诊助手中允许展示的记录分类。 */
export const getAssistantTabs = ['挂号记录', '处方'] as const;

/** 当前挂号流程底部操作的展示类型。 */
export type CurrentFlowAction = 'PAY' | 'WAITING';

/** 就诊助手挂号状态静默刷新间隔，单位毫秒。 */
export const ASSISTANT_APPOINTMENT_REFRESH_INTERVAL_MILLIS = 30000;

/**
 * 根据挂号订单状态确定当前流程允许的操作。
 * @param status 挂号订单当前状态
 * @returns 未支付时允许支付，已支付时仅展示等待状态
 */
export function getCurrentFlowAction(status: Appointment['status']): CurrentFlowAction {
  // 已支付订单等待线下就诊，避免从助手页重复创建预问诊。
  return status === 'UNPAID' ? 'PAY' : 'WAITING';
}
