import { useEffect, useRef, useState } from 'react';
import { Bell, CheckCheck, ChevronDown } from 'lucide-react';
import { useNavigate } from 'umi';
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
  { value: 'CONSULTATION', label: '问诊' },
  { value: 'DRUG_ORDER', label: '购药' },
  { value: 'LOGISTICS', label: '物流' },
];
type NotificationReadFilter = 'READ' | 'UNREAD';

/** 展示当前账号全部站内消息并支持单条标记已读。 */
export default function NotificationsPage() {
  const navigate = useNavigate();
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [category, setCategory] = useState<NotificationListCategory>('ALL');
  const [readFilter, setReadFilter] = useState<NotificationReadFilter>('UNREAD');
  const [loading, setLoading] = useState(true);
  const [readingId, setReadingId] = useState<number>();
  const [markingAll, setMarkingAll] = useState(false);
  const [notice, setNotice] = useState('');
  const readKeys = useRef<Record<number, string>>({});

  /** 获取通知分页数据，翻页时保留已加载的消息。 */
  async function loadNotifications(targetPage = 1, append = false, targetCategory = category, targetReadFilter = readFilter) {
    setLoading(true);
    try {
      const page = await getNotifications({ pageNo: targetPage, pageSize: PAGE_SIZE, read: targetReadFilter === 'READ', type: resolveNotificationListType(targetCategory) });
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
    void loadNotifications(1, false, targetCategory, readFilter);
  }

  /** 切换已读状态筛选时重新读取当前通知类型，避免两种状态混杂。 */
  function changeReadFilter(targetReadFilter: NotificationReadFilter) {
    if (targetReadFilter === readFilter) return;
    setReadFilter(targetReadFilter);
    setNotifications([]);
    setTotal(0);
    setPageNo(1);
    void loadNotifications(1, false, category, targetReadFilter);
  }

  /** 点击消息后先完成已读处理，再进入关联的在线问诊详情。 */
  async function readNotification(notification: NotificationItem) {
    if (readingId || markingAll) return;
    if (notification.read) {
      if (notification.type === 'CONSULTATION' && notification.consultationId) navigate(`/assistant/consultation/${notification.consultationId}`);
      return;
    }
    setReadingId(notification.id);
    const key = readKeys.current[notification.id] = resolveNotificationReadKey(readKeys.current[notification.id]);
    try {
      await markNotificationRead(notification.id, key);
      delete readKeys.current[notification.id];
      setNotifications((current) => current.map((item) => item.id === notification.id ? { ...item, read: true } : item));
      // 医生回复通知必须在已读成功后跳转，避免详情返回后仍显示未读。
      if (notification.type === 'CONSULTATION' && notification.consultationId) navigate(`/assistant/consultation/${notification.consultationId}`);
    } catch (requestError) {
      setNotice(getApiErrorMessage(requestError));
    } finally {
      setReadingId(undefined);
    }
  }

  /** 批量读取账号全部未读通知，再逐条调用后端已读接口。 */
  async function markAllNotificationsRead() {
    if (markingAll) return;
    setMarkingAll(true);
    try {
      // 先完整读取未读快照，再执行写操作，避免标记过程中分页总数变化导致漏读。
      const firstPage = await getNotifications({ read: false, pageNo: 1, pageSize: 100 });
      const pageCount = Math.ceil(firstPage.total / 100);
      const pages = await Promise.all(Array.from({ length: Math.max(0, pageCount - 1) }, (_, index) => getNotifications({ read: false, pageNo: index + 2, pageSize: 100 })));
      const unreadIds = [...firstPage.records, ...pages.flatMap((page) => page.records)].map((item) => item.id).filter((id, index, ids) => ids.indexOf(id) === index);
      if (!unreadIds.length) {
        setNotice('暂无未读消息');
        return;
      }
      // 控制并发批次，避免一次性提交大量已读请求造成客户端和服务端抖动。
      for (let index = 0; index < unreadIds.length; index += 8) {
        await Promise.all(unreadIds.slice(index, index + 8).map(async (id) => {
          const key = readKeys.current[id] = resolveNotificationReadKey(readKeys.current[id]);
          await markNotificationRead(id, key);
          delete readKeys.current[id];
        }));
      }
      await loadNotifications(1, false, category, readFilter);
    } catch (requestError) {
      setNotice(getApiErrorMessage(requestError));
      await loadNotifications(1, false, category, readFilter);
    } finally {
      setMarkingAll(false);
    }
  }

  const hasMore = notifications.length < total;
  return <main className="subpage"><PageHeader title="通知消息" /><section className="subpage-content notification-page"><header className="notification-page__intro"><Bell size={25} /><div><h2>通知</h2></div><button className="notification-mark-all" disabled={markingAll || Boolean(readingId)} type="button" onClick={() => void markAllNotificationsRead()}>{markingAll ? '处理中...' : '一键已读'}</button></header>
    <nav className="notification-filter-tabs" aria-label="通知分类">{NOTIFICATION_CATEGORIES.map((item) => <button className={category === item.value ? 'active' : ''} key={item.value} type="button" onClick={() => changeCategory(item.value)}>{item.label}</button>)}</nav>
    <nav className="notification-read-tabs" aria-label="消息阅读状态"><button className={readFilter === 'READ' ? 'active' : ''} type="button" onClick={() => changeReadFilter('READ')}>已读</button><button className={readFilter === 'UNREAD' ? 'active' : ''} type="button" onClick={() => changeReadFilter('UNREAD')}>未读</button></nav>
    {loading && !notifications.length && <p className="empty-state">正在读取通知消息...</p>}
    {notifications.map((notification) => <button className={notification.read ? 'notification-card' : 'notification-card unread'} disabled={readingId === notification.id} key={notification.id} type="button" onClick={() => void readNotification(notification)}><div className="notification-card__top"><em>{getNotificationTypeText(notification.type)}</em><span>{notification.patientName || '当前账号'}</span>{notification.read ? <CheckCheck size={18} /> : <i>未读</i>}</div><h3>{notification.title}</h3><p>{notification.content}</p><small>{formatMedicalTime(notification.createdAt)}</small></button>)}
    {!loading && !notifications.length && <p className="empty-state">暂无通知消息</p>}
    {hasMore && <button className="load-more-button" disabled={loading} type="button" onClick={() => void loadNotifications(pageNo + 1, true, category)}>{loading ? '加载中...' : <>加载更多 <ChevronDown size={17} /></>}</button>}
  </section>{notice && <div className="toast" role="status" onClick={() => setNotice('')}>{notice}</div>}</main>;
}
