import type { Appointment, FollowUpPlan, MedicationPlan, MedicationPlanAction, NotificationItem, NotificationType } from '../typings/api';
import { createIdempotencyKey } from './form';

/** 首页跨就诊人查询到的原始待办数据。 */
export interface PatientHealthSource { patientId: number; patientName: string; appointments: Appointment[]; medicationPlans: MedicationPlan[]; followUps: FollowUpPlan[]; }
/** 首页统一展示的健康待办卡片数据。 */
export interface HealthTodo { id: number; type: 'APPOINTMENT' | 'MEDICATION' | 'FOLLOW_UP'; patientId: number; patientName: string; title: string; detail: string; departmentLocation?: string; occurredAt?: string; isExpired?: boolean; /** 后端已开启提醒时允许首页执行本地服药确认。 */ reminderEnabled?: boolean; }
/** 用药提醒页可切换的计划分类。 */
export type MedicationPlanTab = 'IN_PROGRESS' | 'COMPLETED';

/** 将通知类型映射为患者可理解的页面文案。 */
export function getNotificationTypeText(type: NotificationType): string {
  return ({ APPOINTMENT: '挂号通知', CONSULTATION: '问诊通知', DRUG_ORDER: '购药通知', LOGISTICS: '物流通知', MEDICATION_REMINDER: '用药提醒', FOLLOW_UP_REMINDER: '随访提醒', SYSTEM: '系统通知' } as Record<NotificationType, string>)[type];
}

/** 通知页可切换的展示分类。 */
export type NotificationListCategory = 'ALL' | 'APPOINTMENT' | 'CONSULTATION' | 'DRUG_ORDER' | 'LOGISTICS';

/**
 * 将通知页分类转换为后端通知类型筛选条件。
 * @param category 当前选择的通知分类
 * @returns 全部分类返回 undefined，其余返回对应的后端类型
 */
export function resolveNotificationListType(category: NotificationListCategory): NotificationType | undefined {
  return category === 'ALL' ? undefined : category;
}

/**
 * 从后端已按时间倒序返回的未读通知中定位最新候补可预约提醒。
 * @param notifications 当前账号的未读通知列表
 * @returns 候补号源可预约通知；不存在时返回 undefined
 */
export function findLatestWaitlistPromotionNotification(notifications: NotificationItem[]): NotificationItem | undefined {
  // 后端使用 APPOINTMENT 类型和固定标题标识候补晋级，避免将普通挂号通知误弹出。
  return notifications.find((notification) => notification.type === 'APPOINTMENT' && notification.title === '候补号源可预约');
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

/**
 * 按用药提醒页的 Tab 筛选计划。
 * @param plans 当前就诊人的全部用药计划
 * @param tab 当前选择的计划分类
 * @returns 与当前 Tab 匹配的用药计划
 */
export function filterMedicationPlansByTab<T extends Pick<MedicationPlan, 'status'>>(plans: T[], tab: MedicationPlanTab): T[] {
  // 暂停计划尚未完成，保留在执行中分类中，避免用户无法恢复或完成该计划。
  return plans.filter((plan) => tab === 'COMPLETED' ? plan.status === 'COMPLETED' : plan.status !== 'COMPLETED');
}

/**
 * 根据服务端提醒开关返回执行中计划可切换的提醒动作。
 * @param plan 用药计划的状态与提醒开关
 * @returns 当前可执行的提醒动作；非执行中计划不展示开关
 */
export function getMedicationReminderAction(plan: Pick<MedicationPlan, 'status' | 'reminderEnabled'>): MedicationPlanAction | undefined {
  // 暂停计划会停止扫描，完成计划已关闭提醒，因此不向后端发送提醒开关动作。
  if (plan.status !== 'ACTIVE') return undefined;
  return plan.reminderEnabled ? 'DISABLE_REMINDER' : 'ENABLE_REMINDER';
}

/**
 * 将后端返回的每日提醒时刻转换为卡片展示文案。
 * @param reminderTimes 服务端按频次生成的日间时刻列表
 * @returns 以中文顿号连接的提醒时刻；没有时刻时返回空字符串
 */
export function formatMedicationReminderTimes(reminderTimes: string[]): string {
  return reminderTimes.filter(Boolean).join('、');
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
  return ({ ENABLE_REMINDER: '开启用药提醒', DISABLE_REMINDER: '关闭用药提醒', PAUSE: '暂停用药', RESUME: '恢复用药', COMPLETE: '完成计划' } as Record<MedicationPlanAction, string>)[action];
}

/**
 * 聚合全部就诊人的待处理挂号、用药和随访计划。
 * @param sources 按就诊人读取的服务端数据
 * @param nowMillis 用于判定预约是否过期的当前时间戳
 * @returns 按处理时间升序排列的首页健康待办
 */
export function buildHealthTodos(sources: PatientHealthSource[], nowMillis = Date.now()): HealthTodo[] {
  const todos = sources.flatMap((source) => [
    // 挂号待办保留服务端返回的科室位置，供首页卡片展示实际就诊地点。
    ...source.appointments.filter((item) => item.status === 'UNPAID' || item.status === 'PAID').map((item) => {
      const appointmentEndAt = item.endTime ? new Date(item.endTime).getTime() : Number.NaN;
      // 仅在服务端返回的号源结束时间已过时，才将待支付或待就诊记录标记为过期。
      const isExpired = !Number.isNaN(appointmentEndAt) && appointmentEndAt <= nowMillis;
      return { id: item.id, type: 'APPOINTMENT' as const, patientId: source.patientId, patientName: source.patientName, title: `${item.departmentName} · ${item.doctorName}`, detail: isExpired ? '已过期' : item.status === 'UNPAID' ? '挂号待支付' : '挂号待就诊', departmentLocation: item.departmentLocation, occurredAt: item.startTime, isExpired };
    }),
    ...source.medicationPlans.filter((item) => item.status === 'ACTIVE').map((item) => ({ id: item.id, type: 'MEDICATION' as const, patientId: source.patientId, patientName: source.patientName, title: item.drugName, detail: `${item.dosage} · ${item.frequency}`, occurredAt: item.nextReminderAt, reminderEnabled: item.reminderEnabled })),
    ...source.followUps.filter((item) => item.status === 'PENDING_CONFIRM' || item.status === 'CONFIRMED').map((item) => ({ id: item.id, type: 'FOLLOW_UP' as const, patientId: source.patientId, patientName: source.patientName, title: item.type || '随访计划', detail: item.content, occurredAt: item.remindAt || item.dueAt })),
  ]);
  // 无时间的数据置后，避免遮挡已确定处理时间的真实待办。
  return todos.sort((left, right) => (Date.parse(left.occurredAt || '') || Number.MAX_SAFE_INTEGER) - (Date.parse(right.occurredAt || '') || Number.MAX_SAFE_INTEGER));
}
