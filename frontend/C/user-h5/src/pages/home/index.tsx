import { useEffect, useRef, useState, type TouchEvent } from 'react';
import { BellRing, CalendarPlus, ChevronRight, ClipboardPlus, FileChartColumn, HeartPulse, MapPin, Pill, Search, Stethoscope, X } from 'lucide-react';
import { useNavigate } from 'umi';
import { BottomTab } from '../../components/BottomTab';
import { Dialog } from '../../components/Dialog';
import { HOME_CONSULTATION_MESSAGE, HOME_TRIAGE_MESSAGE } from '../../constants/agent';
import { completeAllHealthTodos, getCompletedHealthTodoIds, isHealthTodoCompleted } from '../../models/completed-health-todo';
import { dismissExpiredHealthTodo, getDismissedExpiredHealthTodoIds, isExpiredHealthTodoDismissed } from '../../models/expired-health-todo';
import { dismissCompletedConsultationTodo, getDismissedCompletedConsultationTodoIds, isCompletedConsultationTodoDismissed } from '../../models/completed-consultation-health-todo';
import { dismissMedicationHealthTodo, getMedicationHealthTodoState, getMedicationHealthTodoStates, isMedicationHealthTodoDismissed, markMedicationHealthTodoTaken, type MedicationHealthTodoState } from '../../models/medication-health-todo';
import { getSelection, resolveSelectedPatientId, saveSelection } from '../../models/selection';
import { getFamilyMembers } from '../../services/family';
import { getFollowUpPlans, getMedicationPlans } from '../../services/health';
import { getConsultations } from '../../services/consultation';
import { getNotifications } from '../../services/notification';
import { getAppointments, getHospitals } from '../../services/registration';
import type { FamilyMember, Hospital, NotificationItem } from '../../typings/api';
import { buildHealthTodos, findLatestWaitlistPromotionNotification, type HealthTodo, type PatientHealthSource } from '../../utils/health-notification';
import { buildConsultationDetailPath } from '../../utils/consultation';
import { formatMedicalTime, sortHospitals } from '../../utils/medical';
import appointmentBanner from '../../assets/home-banner-appointment.png';
import consultationBanner from '../../assets/home-banner-consultation.png';

const homeBanners = [
  { image: appointmentBanner, label: '预约挂号服务', action: '/home/departments' },
  { image: consultationBanner, label: '在线问诊服务', action: '/assistant' },
];
const circularHomeBanners = [homeBanners[homeBanners.length - 1], ...homeBanners, homeBanners[0]];

/** 展示医院入口、就诊人、快捷服务和全账号健康待办的首页。 */
export default function HomePage() {
  const navigate = useNavigate();
  const [hospitals, setHospitals] = useState<Hospital[]>([]);
  const [members, setMembers] = useState<FamilyMember[]>([]);
  const [selected, setSelected] = useState(getSelection());
  const [patientOpen, setPatientOpen] = useState(false);
  const [notice, setNotice] = useState('');
  const [todos, setTodos] = useState<HealthTodo[]>([]);
  const [medicationTodoStates, setMedicationTodoStates] = useState<Record<string, MedicationHealthTodoState>>({});
  const [waitlistNotification, setWaitlistNotification] = useState<NotificationItem>();
  const [bannerSlideIndex, setBannerSlideIndex] = useState(1);
  const [bannerTransitionEnabled, setBannerTransitionEnabled] = useState(true);
  const waitlistTimer = useRef<number>();
  const swipeStartX = useRef<number>();
  const swipeMoved = useRef(false);
  const bannerTransitioning = useRef(false);
  const bannerResetFrame = useRef<number>();

  /** 读取单个就诊人的三类待办，供首页统一展示。 */
  async function loadPatientHealthSource(member: FamilyMember): Promise<PatientHealthSource> {
    const [appointmentPage, consultationPage, medicationPlans, followUps] = await Promise.all([
      getAppointments(member.patientId, undefined, 100),
      getConsultations(member.patientId),
      getMedicationPlans(member.patientId),
      getFollowUpPlans(member.patientId),
    ]);
    return { patientId: member.patientId, patientName: member.name, appointments: appointmentPage.records, consultations: consultationPage.records, medicationPlans, followUps };
  }

  /** 初始化医院、当前就诊人和账号全部健康待办。 */
  async function loadHome() {
    try {
      // 候补提醒独立读取，通知接口失败不能阻断首页核心数据加载。
      const notificationPromise = getNotifications({ read: false, pageNo: 1, pageSize: 20 }).catch(() => undefined);
      const [nextHospitals, nextMembers] = await Promise.all([getHospitals(), getFamilyMembers()]);
      setHospitals(sortHospitals(nextHospitals));
      setMembers(nextMembers);
      const state = getSelection();
      const next = {
        hospitalId: state.hospitalId || nextHospitals[0]?.hospitalId,
        patientId: resolveSelectedPatientId(nextMembers, state.patientId),
      };
      saveSelection(next);
      setSelected(next);
      const healthResultsPromise = Promise.allSettled(nextMembers.map(loadPatientHealthSource));
      const notificationPage = await notificationPromise;
      if (notificationPage) {
        setWaitlistNotification(findLatestWaitlistPromotionNotification(notificationPage.records));
      }
      // 后端按患者隔离待办，首页需要汇总本人和家属后才能展示账号全部待办。
      const healthResults = await healthResultsPromise;
      const sources = healthResults.filter((item): item is PromiseFulfilledResult<PatientHealthSource> => item.status === 'fulfilled').map((item) => item.value);
      const nextTodos = buildHealthTodos(sources);
      // 已被患者关闭的过期订单在同一登录会话内不再因页面重新加载而重复出现。
      const dismissedTodoIds = getDismissedExpiredHealthTodoIds();
      const nextMedicationTodoStates = getMedicationHealthTodoStates();
      const nextDismissedConsultationTodoIds = getDismissedCompletedConsultationTodoIds();
      const nextCompletedTodoIds = getCompletedHealthTodoIds();
      setMedicationTodoStates(nextMedicationTodoStates);
      setTodos(nextTodos.filter((todo) => (!todo.isExpired || !isExpiredHealthTodoDismissed(todo, dismissedTodoIds))
        // 仅已开启提醒的用药待办支持本地关闭，其他用药卡片保持原跳转行为。
        && !(todo.type === 'MEDICATION' && todo.reminderEnabled && isMedicationHealthTodoDismissed(todo, nextMedicationTodoStates))
        && !(todo.type === 'CONSULTATION' && isCompletedConsultationTodoDismissed(todo, nextDismissedConsultationTodoIds))
        // 一键完成只影响当前会话中的首页提示，刷新数据时继续过滤已完成卡片。
        && !isHealthTodoCompleted(todo, nextCompletedTodoIds)));
      if (healthResults.some((item) => item.status === 'rejected')) setNotice('部分健康待办加载失败，请稍后重试');
    } catch (error: unknown) {
      setNotice(error instanceof Error ? error.message : '首页数据加载失败');
    }
  }

  useEffect(() => { void loadHome(); }, []);

  useEffect(() => {
    if (!waitlistNotification) return undefined;
    // 候补提醒仅短暂展示，未读状态仍由通知消息页统一维护。
    waitlistTimer.current = window.setTimeout(() => setWaitlistNotification(undefined), 5000);
    return () => { if (waitlistTimer.current) window.clearTimeout(waitlistTimer.current); };
  }, [waitlistNotification?.id]);

  useEffect(() => {
    // 减弱动态效果偏好下不自动轮换，避免影响阅读与操作。
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return undefined;
    const timer = window.setInterval(() => changeBanner(1), 5200);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => () => {
    // 页面切走时取消待执行的回正帧，防止卸载后继续更新轮播状态。
    if (bannerResetFrame.current) window.cancelAnimationFrame(bannerResetFrame.current);
  }, []);

  /** 关闭顶部候补提醒，不调用已读接口以保留通知入口的未读红点。 */
  function dismissWaitlistNotification() {
    setWaitlistNotification(undefined);
  }

  /** 打开通知消息页，由现有页面完成单条通知已读处理。 */
  function openWaitlistNotification() {
    setWaitlistNotification(undefined);
    navigate('/mine/notifications');
  }

  /** 根据待办类别跳转到可继续处理的页面。 */
  function openTodo(todo: HealthTodo) {
    if (todo.isExpired) {
      // 过期挂号不再进入流程，关闭状态写入会话以覆盖切页后重新聚合待办的场景。
      dismissExpiredHealthTodo(todo);
      setTodos((current) => current.filter((item) => !(item.type === todo.type && item.id === todo.id && item.patientId === todo.patientId)));
      return;
    }
    if (todo.type === 'MEDICATION' && todo.reminderEnabled) {
      const medicationTodoState = getMedicationHealthTodoState(todo, medicationTodoStates);
      if (medicationTodoState === 'TAKEN') {
        // 第二次点击确认关闭，当前提醒时段不再在本次会话中重复出现。
        const nextStates = dismissMedicationHealthTodo(todo);
        setMedicationTodoStates(nextStates);
        setTodos((current) => current.filter((item) => item.type !== 'MEDICATION' || !item.reminderEnabled || !isMedicationHealthTodoDismissed(item, nextStates)));
      } else {
        // 首次点击只做本地服药确认，不跳转设置页，也不修改后端用药计划。
        setMedicationTodoStates(markMedicationHealthTodoTaken(todo));
      }
      return;
    }
    if (todo.type === 'CONSULTATION') {
      // 点击后先关闭首页提醒，再进入对应问诊详情，避免返回首页时重复出现。
      const nextDismissedIds = dismissCompletedConsultationTodo(todo);
      setTodos((current) => current.filter((item) => item.type !== 'CONSULTATION' || !isCompletedConsultationTodoDismissed(item, nextDismissedIds)));
      navigate(buildConsultationDetailPath(todo.id, todo.patientId));
      return;
    }
    if (todo.type === 'APPOINTMENT') navigate('/assistant');
    else if (todo.type === 'MEDICATION') navigate(`/mine/medication-plans?patientId=${todo.patientId}&source=home`);
    else navigate('/mine/follow-ups');
  }

  /**
   * 将当前展示的全部健康待办标记为已完成。
   * @returns 无返回值
   */
  function completeAllTodos() {
    if (!todos.length) return;
    // 使用当前已渲染集合写入会话存储，避免切换页面后服务端相同待办再次显示。
    completeAllHealthTodos(todos);
    setTodos([]);
    setNotice('已完成全部健康待办');
  }

  /** 沿滑动方向切换宣传窗页码，首尾页通过克隆卡片保持连续运动。 */
  function changeBanner(offset: number) {
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      // 减少动态效果模式下没有 transitionend，直接在真实页之间切换，避免轮播锁无法释放。
      setBannerTransitionEnabled(false);
      setBannerSlideIndex((index) => {
        const currentIndex = index === 0 ? homeBanners.length : index === homeBanners.length + 1 ? 1 : index;
        if (offset > 0) return currentIndex === homeBanners.length ? 1 : currentIndex + 1;
        return currentIndex === 1 ? homeBanners.length : currentIndex - 1;
      });
      return;
    }
    // 自动轮换与快速手势共用此锁，避免过渡未完成时页码越过首尾克隆卡。
    if (bannerTransitioning.current) return;
    bannerTransitioning.current = true;
    setBannerTransitionEnabled(true);
    setBannerSlideIndex((index) => {
      // 若上一次在边界帧被打断，先恢复为真实页再计算下一页。
      const currentIndex = index === 0 ? homeBanners.length : index === homeBanners.length + 1 ? 1 : index;
      return currentIndex + (offset > 0 ? 1 : -1);
    });
  }

  /** 动画到达首尾克隆卡后，无感重置到对应真实卡片。 */
  function normalizeBannerSlide() {
    if (bannerSlideIndex !== 0 && bannerSlideIndex !== homeBanners.length + 1) {
      bannerTransitioning.current = false;
      return;
    }
    // 关闭一次过渡再回到真实卡片，避免循环边界产生反方向回弹。
    setBannerTransitionEnabled(false);
    setBannerSlideIndex(bannerSlideIndex === 0 ? homeBanners.length : 1);
    window.requestAnimationFrame(() => {
      bannerResetFrame.current = window.requestAnimationFrame(() => {
        setBannerTransitionEnabled(true);
        // 回正完成后才允许下一次手势或自动轮换，保证索引始终处于有效范围。
        bannerTransitioning.current = false;
      });
    });
  }

  /** 记录宣传窗手势起点，用于区分点击按钮与左右滑动。 */
  function startBannerSwipe(event: TouchEvent<HTMLElement>) {
    swipeStartX.current = event.touches[0]?.clientX;
    swipeMoved.current = false;
  }

  /** 根据横向位移切换宣传页，短距离触摸不会误触发页面跳转。 */
  function endBannerSwipe(event: TouchEvent<HTMLElement>) {
    const startX = swipeStartX.current;
    const endX = event.changedTouches[0]?.clientX;
    swipeStartX.current = undefined;
    if (startX === undefined || endX === undefined || Math.abs(startX - endX) < 42) return;
    swipeMoved.current = true;
    changeBanner(startX > endX ? 1 : -1);
  }

  /** 打开当前宣传页对应的服务入口，滑动结束后忽略一次合成点击。 */
  function openBanner(path: string) {
    if (swipeMoved.current) {
      swipeMoved.current = false;
      return;
    }
    // 咨询轮播卡与首页“在线问诊”入口保持一致，进入 Agent 后自动发送在线问诊指令。
    if (path === '/assistant') {
      navigate('/agent', { state: { from: '/home', presetAction: { type: 'quick_message', content: HOME_CONSULTATION_MESSAGE } } });
      return;
    }
    navigate(path);
  }

  /** 切换首页当前就诊人，并保留已选择的医院。 */
  function selectPatient(patientId: number) {
    // 选择结果写入跨页面状态，后续挂号、问诊等入口读取同一就诊人。
    saveSelection({ patientId });
    setSelected(getSelection());
    setPatientOpen(false);
  }

  const currentHospital = hospitals.find((item) => item.hospitalId === selected.hospitalId);
  const currentPatient = members.find((item) => item.patientId === selected.patientId);
  const services = [
    { label: '预约挂号', icon: CalendarPlus, action: () => navigate('/home/departments') },
    { label: '智能导诊', icon: Stethoscope, action: () => navigate('/agent', { state: { from: '/home', presetAction: { type: 'quick_message', content: HOME_TRIAGE_MESSAGE } } }) },
    { label: '在线问诊', icon: HeartPulse, action: () => navigate('/agent', { state: { from: '/home', presetAction: { type: 'quick_message', content: HOME_CONSULTATION_MESSAGE } } }) },
    { label: '处方购药', icon: Pill, action: () => navigate('/pharmacy') },
    { label: '病历报告', icon: FileChartColumn, action: () => navigate('/medical-records?source=home') },
    { label: '用药提醒', icon: ClipboardPlus, action: () => navigate('/mine/medication-plans?source=home') },
  ];

  return <main className="home-page">
    <section className="home-overview">
      <header className="home-appbar">
        <button className="home-appbar__hospital" type="button" onClick={() => navigate('/home/hospitals')}>
          <MapPin size={20} /><span>{currentHospital?.name || '选择医院'}</span><ChevronRight size={17} />
        </button>
        <span className="home-appbar__brand"><b>智</b><strong>智慧先锋</strong></span>
      </header>
      <button className="home-search-bar" type="button" onClick={() => navigate('/home/departments')}><Search size={22} /><span>搜索医院、科室、疾病、医生</span></button>
      <section className="home-promo" aria-label="医疗服务宣传" onTouchStart={startBannerSwipe} onTouchEnd={endBannerSwipe}>
        <div className="home-promo__track" style={{ transform: `translateX(-${bannerSlideIndex * 100}%)`, transition: bannerTransitionEnabled ? undefined : 'none' }} onTransitionEnd={normalizeBannerSlide}>
          {circularHomeBanners.map((banner, index) => <button className={`home-promo__slide${banner.label === '在线问诊服务' ? ' home-promo__slide--consultation' : ''}`} key={`${banner.label}-${index}`} type="button" aria-label={banner.label} onClick={() => openBanner(banner.action)}><img src={banner.image} alt="" /></button>)}
        </div>
        <div className="home-promo__pager" aria-hidden="true">{homeBanners.map((banner, index) => <i className={index === (bannerSlideIndex - 1 + homeBanners.length) % homeBanners.length ? 'is-active' : ''} key={banner.label} />)}</div>
      </section>
    </section>
    {waitlistNotification && <section className="waitlist-banner" aria-label="候补可预约提醒"><button className="waitlist-banner__body" type="button" onClick={openWaitlistNotification}><span className="waitlist-banner__icon"><BellRing size={23} /></span><span className="waitlist-banner__content"><b>{waitlistNotification.title}</b><span>{waitlistNotification.content}</span><small>{waitlistNotification.patientName || '当前就诊人'}</small></span></button><button className="waitlist-banner__close" type="button" aria-label="关闭候补提醒" onClick={dismissWaitlistNotification}><X size={18} /></button></section>}
    <header className="home-hero"><div className="home-brand"><b>智</b><div><strong>智愈先锋</strong><span>省人民医院智慧医疗服务</span></div></div><p>让每一次就医，都更清晰、更安心</p></header>
    <section className="home-content">
      <button className="hospital-switch" type="button" onClick={() => navigate('/home/hospitals')}>当前医院：{currentHospital?.name || '选择医院'} <ChevronRight size={18} /></button>
      <button className="search-bar" type="button" onClick={() => navigate('/home/departments')}><Search size={24} /><span>搜索医生、科室</span></button>
      <section className="patient-switch-card"><div><b>当前就诊人 · {currentPatient?.name || '未选择'}</b><p>{currentPatient?.phone || '资料待完善'}</p></div><button type="button" className="text-button" onClick={() => setPatientOpen(true)}>切换 <ChevronRight size={19} /></button></section>
      <h2>快捷服务</h2>
      <section className="quick-grid">{services.map(({ label, icon: Icon, action }) => <button key={label} type="button" onClick={action || (() => setNotice(`${label}暂未开放`))}><Icon size={29} /><span>{label}</span></button>)}</section>
      <section className="todo-section"><div className="section-title"><h2>健康待办</h2>{todos.length > 0 && <span className="todo-actions"><span className="todo-count">{todos.length} 项待处理</span><button className="todo-complete-all" type="button" onClick={completeAllTodos}>一键完成</button></span>}</div>
        {todos.map((todo) => {
          const medicationTaken = todo.type === 'MEDICATION' && todo.reminderEnabled && getMedicationHealthTodoState(todo, medicationTodoStates) === 'TAKEN';
          return <button className={`${todo.type === 'APPOINTMENT' ? 'health-todo-card has-location' : 'health-todo-card'}${todo.isExpired ? ' is-expired' : ''}${medicationTaken ? ' is-medication-taken' : ''}`} type="button" key={`${todo.type}-${todo.id}-${todo.patientId}-${todo.occurredAt || ''}`} onClick={() => openTodo(todo)}><div className={`health-todo-card__icon ${todo.type.toLowerCase()}`}>{todo.type === 'APPOINTMENT' ? '挂' : todo.type === 'MEDICATION' ? '药' : todo.type === 'CONSULTATION' ? '诊' : '访'}</div><div><b>{todo.occurredAt ? formatMedicalTime(todo.occurredAt) : '时间待确认'} · {todo.title}</b><span>{todo.patientName} · {todo.detail}</span>{todo.type === 'APPOINTMENT' && <small>科室位置：{todo.departmentLocation || '科室位置待确认'}</small>}</div>{medicationTaken ? <em className="health-todo-card__taken">已服用</em> : <ChevronRight size={18} />}</button>;
        })}
        {!todos.length && <p className="empty-state">暂无健康待办</p>}
      </section>
    </section>
    {patientOpen && <Dialog title="切换就诊人" onClose={() => setPatientOpen(false)}>{members.map((member) => <button className="choice-row" key={member.patientId} type="button" onClick={() => selectPatient(member.patientId)}><span>{member.name}</span><small>{member.relationName || member.relation}{member.patientId === selected.patientId ? ' · 当前选择' : ''}</small></button>)}{members.filter((member) => member.relation !== 'SELF').length === 0 && <p className="empty-state">当前用户未绑定亲属</p>}</Dialog>}
    {notice && <div className="toast" role="status" onClick={() => setNotice('')}>{notice}</div>}
    <BottomTab onUnavailable={() => setNotice('该页面暂未开放')} />
  </main>;
}
