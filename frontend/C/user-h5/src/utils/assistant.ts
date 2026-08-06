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

/**
 * 判断挂号订单是否应在就诊助手中展示为当前流程。
 * @param appointment 挂号订单列表项
 * @param now 当前时间戳，便于验证时段结束后的展示状态
 * @returns 未结束的待支付或已支付订单返回 true
 */
export function isCurrentAssistantFlow(appointment: Appointment, now = Date.now()): boolean {
  // 已取消、完成等终态订单只保留在挂号记录，不进入当前流程。
  if (appointment.status !== 'UNPAID' && appointment.status !== 'PAID') return false;
  const endAt = appointment.endTime === undefined ? Number.NaN : new Date(appointment.endTime).getTime();
  // 结束时间缺失时不误隐藏当前订单；服务端已在挂号列表中返回该字段。
  return !Number.isFinite(endAt) || endAt > now;
}
