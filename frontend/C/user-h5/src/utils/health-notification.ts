import type { Appointment, FollowUpPlan, MedicationPlan, MedicationPlanAction, NotificationType } from '../typings/api';
import { createIdempotencyKey } from './form';

/** 首页跨就诊人查询到的原始待办数据。 */
export interface PatientHealthSource { patientId: number; patientName: string; appointments: Appointment[]; medicationPlans: MedicationPlan[]; followUps: FollowUpPlan[]; }
/** 首页统一展示的健康待办卡片数据。 */
export interface HealthTodo { id: number; type: 'APPOINTMENT' | 'MEDICATION' | 'FOLLOW_UP'; patientId: number; patientName: string; title: string; detail: string; occurredAt?: string; }

/** 将通知类型映射为患者可理解的页面文案。 */
export function getNotificationTypeText(type: NotificationType): string {
  return ({ APPOINTMENT: '挂号通知', DRUG_ORDER: '购药通知', MEDICATION_REMINDER: '用药提醒', FOLLOW_UP_REMINDER: '随访提醒', SYSTEM: '系统通知' } as Record<NotificationType, string>)[type];
}

/**
 * 为通知已读操作生成或复用幂等键。
 * @param existingKey 同一通知网络重试时已保存的幂等键
 * @returns 可用于单条通知已读请求的 UUID
 */
export function resolveNotificationReadKey(existingKey?: string): string {
  return existingKey || createIdempotencyKey();
}

/** 返回当前用药状态允许展示的操作。 */
export function getMedicationPlanActions(status: MedicationPlan['status']): MedicationPlanAction[] {
  if (status === 'ACTIVE') return ['PAUSE', 'COMPLETE'];
  if (status === 'PAUSED') return ['RESUME', 'COMPLETE'];
  return [];
}

/** 判断随访计划是否仍可由患者确认。 */
export function canConfirmFollowUp(status: FollowUpPlan['status']): boolean {
  return status === 'PENDING_CONFIRM';
}

/** 将用药计划状态转换为页面显示文案。 */
export function getMedicationPlanStatusText(status: MedicationPlan['status']): string {
  return ({ ACTIVE: '执行中', PAUSED: '已暂停', COMPLETED: '已完成' } as Record<MedicationPlan['status'], string>)[status];
}

/** 将随访计划状态转换为页面显示文案。 */
export function getFollowUpStatusText(status: FollowUpPlan['status']): string {
  return ({ PENDING_CONFIRM: '待确认', CONFIRMED: '已确认', COMPLETED: '已完成', CANCELLED: '已取消' } as Record<FollowUpPlan['status'], string>)[status];
}

/** 将后端操作编码转换为按钮文案。 */
export function getMedicationActionText(action: MedicationPlanAction): string {
  return ({ PAUSE: '暂停用药', RESUME: '恢复用药', COMPLETE: '完成计划' } as Record<MedicationPlanAction, string>)[action];
}

/**
 * 聚合全部就诊人的待处理挂号、用药和随访计划。
 * @param sources 按就诊人读取的服务端数据
 * @returns 按处理时间升序排列的首页健康待办
 */
export function buildHealthTodos(sources: PatientHealthSource[]): HealthTodo[] {
  const todos = sources.flatMap((source) => [
    ...source.appointments.filter((item) => item.status === 'UNPAID' || item.status === 'PAID').map((item) => ({ id: item.id, type: 'APPOINTMENT' as const, patientId: source.patientId, patientName: source.patientName, title: `${item.departmentName} · ${item.doctorName}`, detail: item.status === 'UNPAID' ? '挂号待支付' : '挂号待就诊', occurredAt: item.startTime })),
    ...source.medicationPlans.filter((item) => item.status === 'ACTIVE').map((item) => ({ id: item.id, type: 'MEDICATION' as const, patientId: source.patientId, patientName: source.patientName, title: item.drugName, detail: `${item.dosage} · ${item.frequency}`, occurredAt: item.nextReminderAt })),
    ...source.followUps.filter((item) => item.status === 'PENDING_CONFIRM' || item.status === 'CONFIRMED').map((item) => ({ id: item.id, type: 'FOLLOW_UP' as const, patientId: source.patientId, patientName: source.patientName, title: item.type || '随访计划', detail: item.content, occurredAt: item.remindAt || item.dueAt })),
  ]);
  // 无时间的数据置后，避免遮挡已确定处理时间的真实待办。
  return todos.sort((left, right) => (Date.parse(left.occurredAt || '') || Number.MAX_SAFE_INTEGER) - (Date.parse(right.occurredAt || '') || Number.MAX_SAFE_INTEGER));
}
