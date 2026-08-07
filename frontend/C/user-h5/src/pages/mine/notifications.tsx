import { useEffect, useRef, useState } from 'react';
import { Bell, CheckCheck, ChevronDown } from 'lucide-react';
import { PageHeader } from '../../components/PageHeader';
import { getNotifications, markNotificationRead } from '../../services/notification';
import type { NotificationItem } from '../../typings/api';
import { getApiErrorMessage } from '../../utils/form';
import { getNotificationTypeText, resolveNotificationListType, resolveNotificationReadKey, type NotificationListCategory } from '../../utils/health-notification';
import { formatMedicalTime } from '../../utils/medical';

const PAGE_SIZE = 20;
const NOTIFICATION_CATEGORIES: { value: NotificationListCategory; label: string }[] = [
  { value: 'ALL', label: '全部' },
  { value: 'APPOINTMENT', label: '挂号' },
  { value: 'DRUG_ORDER', label: '购药' },
  { value: 'LOGISTICS', label: '物流' },
];

/** 展示当前账号全部站内消息并支持单条标记已读。 */
export default function NotificationsPage() {
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [category, setCategory] = useState<NotificationListCategory>('ALL');
  const [loading, setLoading] = useState(true);
  const [readingId, setReadingId] = useState<number>();
  const [notice, setNotice] = useState('');
  const readKeys = useRef<Record<number, string>>({});

  /** 获取通知分页数据，翻页时保留已加载的消息。 */
  async function loadNotifications(targetPage = 1, append = false, targetCategory = category) {
    setLoading(true);
    try {
      const page = await getNotifications({ pageNo: targetPage, pageSize: PAGE_SIZE, type: resolveNotificationListType(targetCategory) });
      setNotifications((current) => append ? [...current, ...page.records] : page.records);
      setTotal(page.total);
      setPageNo(page.pageNo);
    } catch (requestError) {
      setNotice(getApiErrorMessage(requestError));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void loadNotifications(); }, []);

  /** 切换通知分类时从第一页重新读取，避免不同分类混入同一列表。 */
  function changeCategory(targetCategory: NotificationListCategory) {
    if (targetCategory === category) return;
    setCategory(targetCategory);
    setNotifications([]);
    setTotal(0);
    setPageNo(1);
    void loadNotifications(1, false, targetCategory);
  }

  /** 点击未读消息后标记已读，网络重试继续使用第一次生成的幂等键。 */
  async function readNotification(notification: NotificationItem) {
    if (notification.read || readingId) return;
    setReadingId(notification.id);
    const key = readKeys.current[notification.id] = resolveNotificationReadKey(readKeys.current[notification.id]);
    try {
      await markNotificationRead(notification.id, key);
      delete readKeys.current[notification.id];
      setNotifications((current) => current.map((item) => item.id === notification.id ? { ...item, read: true } : item));
    } catch (requestError) {
      setNotice(getApiErrorMessage(requestError));
    } finally {
      setReadingId(undefined);
    }
  }

  const hasMore = notifications.length < total;
  return <main className="subpage"><PageHeader title="通知消息" /><section className="subpage-content notification-page"><header className="notification-page__intro"><Bell size={25} /><div><h2>全部通知</h2><p>挂号、购药、物流、用药与随访消息会在这里显示</p></div></header>
    <nav className="notification-filter-tabs" aria-label="通知分类">{NOTIFICATION_CATEGORIES.map((item) => <button className={category === item.value ? 'active' : ''} key={item.value} type="button" onClick={() => changeCategory(item.value)}>{item.label}</button>)}</nav>
    {loading && !notifications.length && <p className="empty-state">正在读取通知消息...</p>}
    {notifications.map((notification) => <button className={notification.read ? 'notification-card' : 'notification-card unread'} disabled={readingId === notification.id} key={notification.id} type="button" onClick={() => void readNotification(notification)}><div className="notification-card__top"><em>{getNotificationTypeText(notification.type)}</em><span>{notification.patientName || '当前账号'}</span>{notification.read ? <CheckCheck size={18} /> : <i>未读</i>}</div><h3>{notification.title}</h3><p>{notification.content}</p><small>{formatMedicalTime(notification.createdAt)}</small></button>)}
    {!loading && !notifications.length && <p className="empty-state">暂无通知消息</p>}
    {hasMore && <button className="load-more-button" disabled={loading} type="button" onClick={() => void loadNotifications(pageNo + 1, true, category)}>{loading ? '加载中...' : <>加载更多 <ChevronDown size={17} /></>}</button>}
  </section>{notice && <div className="toast" role="status" onClick={() => setNotice('')}>{notice}</div>}</main>;
}
