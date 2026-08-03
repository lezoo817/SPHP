import { FormEvent, useEffect, useRef, useState } from 'react';
import { CalendarClock, HeartPulse } from 'lucide-react';
import { Dialog } from '../../components/Dialog';
import { PageHeader } from '../../components/PageHeader';
import { getFamilyMembers } from '../../services/family';
import { confirmFollowUpPlan, getFollowUpPlans } from '../../services/health';
import { getMinePatientId, resolveMinePatientId, saveMinePatientId } from '../../models/mine-patient';
import type { FollowUpPlan } from '../../typings/api';
import { createIdempotencyKey, getApiErrorMessage } from '../../utils/form';
import { canConfirmFollowUp, getFollowUpStatusText } from '../../utils/health-notification';
import { formatMedicalTime } from '../../utils/medical';

/** 展示当前“我的”就诊人的随访计划，并允许确认待处理计划。 */
export default function FollowUpsPage() {
  const [plans, setPlans] = useState<FollowUpPlan[]>([]);
  const [patientName, setPatientName] = useState('当前就诊人');
  const [loading, setLoading] = useState(true);
  const [confirming, setConfirming] = useState<FollowUpPlan>();
  const [remindAt, setRemindAt] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [notice, setNotice] = useState('');
  const confirmKeys = useRef<Record<number, string>>({});

  /** 依据“我的”专属就诊人选择读取随访计划。 */
  async function loadPlans() {
    setLoading(true);
    try {
      const members = await getFamilyMembers();
      const patientId = resolveMinePatientId(members, getMinePatientId());
      if (!patientId) {
        setPlans([]);
        setNotice('暂无可查询的就诊人');
        return;
      }
      saveMinePatientId(patientId);
      setPatientName(members.find((item) => item.patientId === patientId)?.name || '当前就诊人');
      setPlans(await getFollowUpPlans(patientId));
    } catch (requestError) {
      setNotice(getApiErrorMessage(requestError));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void loadPlans(); }, []);

  /** 打开待确认计划的提醒时间确认窗口。 */
  function openConfirm(plan: FollowUpPlan) {
    setRemindAt('');
    setConfirming(plan);
  }

  /** 提交随访确认，未设置提醒时间时由后端回退使用截止时间。 */
  async function submitConfirm(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!confirming) return;
    setSubmitting(true);
    const key = confirmKeys.current[confirming.id] || (confirmKeys.current[confirming.id] = createIdempotencyKey());
    try {
      const normalizedRemindAt = remindAt ? new Date(remindAt).toISOString() : undefined;
      await confirmFollowUpPlan(confirming.id, normalizedRemindAt, key);
      delete confirmKeys.current[confirming.id];
      setConfirming(undefined);
      await loadPlans();
    } catch (requestError) {
      // 服务端状态改变后刷新列表，不再展示已经失效的确认操作。
      if (typeof requestError === 'object' && requestError !== null && 'status' in requestError && requestError.status === 409) {
        delete confirmKeys.current[confirming.id];
        setConfirming(undefined);
        await loadPlans();
      }
      setNotice(getApiErrorMessage(requestError));
    } finally {
      setSubmitting(false);
    }
  }

  return <main className="subpage"><PageHeader title="随访计划" /><section className="subpage-content plan-page"><header className="plan-page__intro"><HeartPulse size={25} /><div><h2>{patientName}的随访计划</h2><p>确认后将按照设定时间提醒</p></div></header>
    {loading && <p className="empty-state">正在读取随访计划...</p>}
    {!loading && plans.map((plan) => <article className="plan-card" key={plan.id}><div className="plan-card__top"><div><h3>{plan.type || '随访计划'}</h3><p>{plan.content}</p></div><em className={`status-tag ${plan.status.toLowerCase()}`}>{getFollowUpStatusText(plan.status)}</em></div><p className="plan-time"><CalendarClock size={17} />截止时间：{plan.dueAt ? formatMedicalTime(plan.dueAt) : '暂未设置'}</p><p className="plan-time">提醒时间：{plan.remindAt ? formatMedicalTime(plan.remindAt) : '确认后设置'}</p>{canConfirmFollowUp(plan.status) && <button className="plan-action-button full-width" type="button" onClick={() => openConfirm(plan)}>确认随访计划</button>}</article>)}
    {!loading && !plans.length && <p className="empty-state">暂无随访计划</p>}
  </section>{notice && <div className="toast" role="status" onClick={() => setNotice('')}>{notice}</div>}{confirming && <Dialog title="确认随访计划" onClose={() => setConfirming(undefined)}><form className="form-stack" onSubmit={submitConfirm}><p className="dialog-hint">未选择提醒时间时，将以计划截止时间作为提醒时间。</p><label>提醒时间（可选）<input type="datetime-local" value={remindAt} onChange={(event) => setRemindAt(event.target.value)} /></label><button className="primary-button" disabled={submitting} type="submit">{submitting ? '确认中...' : '确认计划'}</button></form></Dialog>}</main>;
}
