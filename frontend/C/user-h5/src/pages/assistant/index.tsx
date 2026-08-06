import { useEffect, useState } from 'react';
import { ChevronRight, RefreshCw } from 'lucide-react';
import { useNavigate } from 'umi';
import { BottomTab } from '../../components/BottomTab';
import { Dialog } from '../../components/Dialog';
import { resolveSelfPatientId } from '../../models/selection';
import { getPrescriptions } from '../../services/consultation';
import { getFamilyMembers } from '../../services/family';
import { cancelAppointment, getAppointment, getAppointments } from '../../services/registration';
import type { Appointment, FamilyMember, Prescription } from '../../typings/api';
import { ASSISTANT_APPOINTMENT_REFRESH_INTERVAL_MILLIS, getAssistantTabs, getCurrentFlowAction, isCurrentAssistantFlow } from '../../utils/assistant';
import { createIdempotencyKey, getApiErrorMessage } from '../../utils/form';
import { formatMedicalTime, getAppointmentStatusText } from '../../utils/medical';
import { buildAssistantPrescriptionDetailPath, getPrescriptionDisplayNumber } from '../../utils/prescription';
import { canCancelPaidAppointment } from '../../utils/registration';

type AssistantTab = (typeof getAssistantTabs)[number];
type FlowStepState = 'done' | 'active' | 'pending';

/** 当前挂号流程中的单个展示步骤。 */
interface FlowStep {
  /** 步骤名称。 */
  label: string;
  /** 步骤根据订单状态得出的展示状态。 */
  state: FlowStepState;
}

/**
 * 根据可联调的挂号订单状态构造当前流程，未实现的医疗环节保持待进行。
 * @param status 当前挂号订单状态
 * @returns 适合纵向进度组件渲染的流程步骤
 */
function getFlowSteps(status: Appointment['status']): FlowStep[] {
  const hasPaid = status === 'PAID';
  return [
    { label: '预约挂号', state: 'done' },
    { label: hasPaid ? '等待就诊' : '门诊缴费', state: 'active' },
    { label: '检验检查', state: 'pending' },
    { label: '查看报告', state: 'pending' },
  ];
}

/** 展示当前挂号流程、挂号记录和处方入口。 */
export default function AssistantPage() {
  const navigate = useNavigate();
  const [members, setMembers] = useState<FamilyMember[]>([]);
  const [patientId, setPatientId] = useState<number>();
  const [appointments, setAppointments] = useState<Appointment[]>([]);
  const [prescriptions, setPrescriptions] = useState<Prescription[]>([]);
  const [open, setOpen] = useState(false);
  const [cancellingAppointment, setCancellingAppointment] = useState<Appointment>();
  const [cancellationPassword, setCancellationPassword] = useState('');
  const [cancelling, setCancelling] = useState(false);
  const [tab, setTab] = useState<AssistantTab>('挂号记录');
  const [notice, setNotice] = useState('');
  const current = members.find((item) => item.patientId === patientId);
  // 号源结束后订单仍保留在挂号记录，但不应继续占用当前就诊流程卡片。
  const currentFlow = appointments.find((item) => isCurrentAssistantFlow(item));

  /**
   * 按当前就诊人刷新助手页服务端数据。
   * @param silent 是否静默处理轮询失败，避免定时刷新重复打断患者操作
   */
  async function loadData(silent = false) {
    try {
      const nextMembers = await getFamilyMembers();
      setMembers(nextMembers);
      const targetPatientId = patientId || resolveSelfPatientId(nextMembers);
      if (!targetPatientId) return;
      if (!patientId) setPatientId(targetPatientId);
      // 两类列表均使用同一就诊人，切换家属后不会混合展示他人的数据。
      const [appointmentPage, prescriptionPage] = await Promise.all([
        getAppointments(targetPatientId),
        getPrescriptions({ patientId: targetPatientId }),
      ]);
      setAppointments(appointmentPage.records);
      setPrescriptions(prescriptionPage.records);
    } catch (error) {
      // 定时刷新失败时保留当前页面数据，首次加载和用户主动操作仍反馈错误原因。
      if (!silent) setNotice(getApiErrorMessage(error));
    }
  }

  useEffect(() => {
    void loadData();
    // 页面存活期间轮询挂号状态，使医生结束接诊和时段结束后的派生状态自动更新。
    const refreshTimer = window.setInterval(() => {
      if (document.visibilityState === 'visible') void loadData(true);
    }, ASSISTANT_APPOINTMENT_REFRESH_INTERVAL_MILLIS);
    // 从后台返回前台时立即刷新，避免患者等待下一轮轮询才看到最终状态。
    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') void loadData(true);
    };
    document.addEventListener('visibilitychange', handleVisibilityChange);
    return () => {
      window.clearInterval(refreshTimer);
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
  }, [patientId]);

  /** 根据未支付订单状态进入支付页，并从详情读取可靠的支付单 ID。 */
  async function continueCurrentFlow(appointment: Appointment) {
    // 状态变化后不再允许旧事件继续进入支付流程。
    if (appointment.status !== 'UNPAID') return;
    try {
      // 列表契约不含 paymentId，支付入口必须查询订单详情后再跳转。
      const detail = await getAppointment(appointment.id);
      if (!detail.payment?.id) {
        setNotice('当前订单暂未生成支付单');
        return;
      }
      navigate(`/assistant/pay/${detail.payment.id}?appointmentId=${appointment.id}`);
    } catch (error) {
      setNotice(getApiErrorMessage(error));
    }
  }

  /** 打开已支付挂号取消确认框，并清理上一次敏感输入。 */
  function openPaidCancellation(appointment: Appointment) {
    if (!canCancelPaidAppointment(appointment.status, appointment.startTime)) return;
    setCancellationPassword('');
    setCancellingAppointment(appointment);
  }

  /** 使用当前登录密码取消尚未开始的已支付挂号并刷新挂号流程。 */
  async function cancelPaidAppointment() {
    if (!cancellingAppointment || !cancellationPassword) {
      setNotice('请输入登录密码');
      return;
    }
    setCancelling(true);
    try {
      await cancelAppointment(cancellingAppointment.id, createIdempotencyKey(), cancellationPassword);
      // 取消完成后立即清理密码，避免敏感内容继续留在页面状态中。
      setCancellationPassword('');
      setCancellingAppointment(undefined);
      await loadData();
      setNotice('挂号已取消');
    } catch (error) {
      setNotice(getApiErrorMessage(error));
    } finally {
      setCancelling(false);
    }
  }

  /** 选择就诊人后关闭弹层，由 patientId 变化重新读取该患者的数据。 */
  function selectPatient(nextPatientId: number) {
    setPatientId(nextPatientId);
    setOpen(false);
  }

  return <main className="assistant-page">
    <header className="assistant-title"><h1>就诊助手</h1></header>
    <section className="assistant-content">
      <button className="assistant-patient" type="button" onClick={() => setOpen(true)}>
        <span className="assistant-patient__name">{current?.name || '选择就诊人'}</span>
        <span>{current?.phone || ''}</span>
        <b>切换就诊人 <RefreshCw size={18} /></b>
      </button>

      {currentFlow ? <section className="flow-card">
        <h2>当前就诊流程 <em>{currentFlow.status === 'UNPAID' ? '待支付' : '待就诊'}</em></h2>
        <div className="flow-highlight">
          <b>{formatMedicalTime(currentFlow.startTime)} · {currentFlow.departmentName}</b>
          <span>{currentFlow.doctorName}</span>
          <small>科室位置：{currentFlow.departmentLocation || '科室位置待确认'}</small>
        </div>
        <ol className="flow-steps">
          {getFlowSteps(currentFlow.status).map((step) => <li className={`flow-step is-${step.state}`} key={step.label}>
            <i className="flow-step__dot" aria-hidden="true" />
            <b>{step.label}</b>
            <small>{step.state === 'done' ? '已完成' : step.state === 'active' ? '当前步骤' : '待进行'}</small>
          </li>)}
        </ol>
        {getCurrentFlowAction(currentFlow.status) === 'PAY' ? <button className="primary-button" type="button" onClick={() => void continueCurrentFlow(currentFlow)}>立即支付</button> : <><button className="primary-button assistant-waiting-button" type="button" disabled>等待就诊中...</button>{canCancelPaidAppointment(currentFlow.status, currentFlow.startTime) && <button className="secondary-button" type="button" onClick={() => openPaidCancellation(currentFlow)}>取消挂号</button>}</>}
      </section> : <section className="flow-card empty-state">
        暂无进行中的就诊流程<br />
        <button className="primary-button" type="button" onClick={() => navigate('/assistant/book')}>去预约挂号</button>
      </section>}

      <div className="assistant-tabs">
        {getAssistantTabs.map((item) => <button key={item} type="button" className={tab === item ? 'active' : ''} onClick={() => setTab(item)}>{item}</button>)}
      </div>
      {tab === '挂号记录' && appointments.map((item) => <article className="record-card" key={item.id}>
        <span className="record-card__main"><b>{formatMedicalTime(item.startTime)}</b><span>{item.departmentName} · {item.doctorName}</span><small>科室位置：{item.departmentLocation || '科室位置待确认'}</small></span>
        <aside className="registration-record-actions"><em className={`record-card__status status-${item.status.toLowerCase()}`}>{getAppointmentStatusText(item.status)}</em>{canCancelPaidAppointment(item.status, item.startTime) && <button className="text-button" type="button" onClick={() => openPaidCancellation(item)}>取消挂号</button>}</aside>
      </article>)}
      {tab === '处方' && prescriptions.map((item) => <button className="record-card" key={item.id} type="button" onClick={() => navigate(buildAssistantPrescriptionDetailPath(item.id, patientId, item.issuedAt))}><span className="record-card__main"><b>{item.doctorName}电子处方</b><span>开具于 {formatMedicalTime(item.issuedAt)}</span><small>处方编号：{getPrescriptionDisplayNumber(item.id, item.issuedAt)}</small></span><ChevronRight size={18} /></button>)}
    </section>
    {open && <Dialog title="切换就诊人" onClose={() => setOpen(false)}>
      {members.map((item) => <button className="choice-row" key={item.patientId} type="button" onClick={() => selectPatient(item.patientId)}>{item.name}<small>{item.relationName}</small></button>)}
      {members.filter((item) => item.relation !== 'SELF').length === 0 && <p className="empty-state">当前用户未绑定亲属</p>}
    </Dialog>}
    {cancellingAppointment && <Dialog title="取消已支付挂号" onClose={() => { if (!cancelling) { setCancellationPassword(''); setCancellingAppointment(undefined); } }}>
      <p className="dialog-hint">取消后将释放该时段号源，模拟支付单仍保留支付成功记录。</p>
      <label>登录密码<input autoComplete="current-password" type="password" value={cancellationPassword} onChange={(event) => setCancellationPassword(event.target.value)} /></label>
      <button className="primary-button" disabled={cancelling} type="button" onClick={() => void cancelPaidAppointment()}>{cancelling ? '取消中...' : '确认取消挂号'}</button>
    </Dialog>}
    {notice && <div className="toast" onClick={() => setNotice('')}>{notice}</div>}
    <BottomTab onUnavailable={() => setNotice('该页面暂未开放')} />
  </main>;
}
