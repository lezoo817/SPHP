import type { NotificationItem, NotificationReadResult, NotificationType, PageData } from '../typings/api';
import { request } from './request';

/** 通知列表的可选筛选条件。 */
export interface NotificationListQuery { patientId?: number; read?: boolean; type?: NotificationType; pageNo?: number; pageSize?: number; }

/**
 * 构建通知列表路径，避免将空筛选条件传给后端。
 * @param query 可选就诊人、已读状态、通知类型与分页条件
 * @returns C 端通知列表请求路径
 */
export function buildNotificationsPath(query: NotificationListQuery = {}): string {
  const params = new URLSearchParams({ pageNo: String(query.pageNo || 1), pageSize: String(query.pageSize || 20) });
  if (query.patientId !== undefined) params.set('patientId', String(query.patientId));
  if (query.read !== undefined) params.set('read', String(query.read));
  if (query.type !== undefined) params.set('type', query.type);
  return `/c/v1/notifications?${params.toString()}`;
}

/** 查询当前账号的站内通知分页数据。 */
export function getNotifications(query: NotificationListQuery = {}): Promise<PageData<NotificationItem>> {
  return request(buildNotificationsPath(query), { method: 'GET' });
}

/** 标记当前账号的一条通知已读。 */
export function markNotificationRead(notificationId: number, idempotencyKey: string): Promise<NotificationReadResult> {
  return request(`/c/v1/notifications/${notificationId}/read`, { method: 'POST', headers: { 'X-Idempotency-Key': idempotencyKey } });
}
