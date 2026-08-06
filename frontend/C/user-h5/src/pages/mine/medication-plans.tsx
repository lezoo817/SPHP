import { useEffect, useRef, useState } from 'react';
import { Clock3, Pill } from 'lucide-react';
import { PageHeader } from '../../components/PageHeader';
import { getFamilyMembers } from '../../services/family';
import { getMedicationPlans, updateMedicationPlan } from '../../services/health';
import { getMinePatientId, resolveMinePatientId, saveMinePatientId } from '../../models/mine-patient';
import type { MedicationPlan, MedicationPlanAction } from '../../typings/api';
import { getApiErrorMessage, createIdempotencyKey } from '../../utils/form';
import { formatMedicationReminderTimes, getMedicationActionText, getMedicationPlanActions, getMedicationPlanStatusText, getMedicationReminderAction } from '../../utils/health-notification';
import { formatMedicalTime } from '../../utils/medical';

/** 展示当前“我的”就诊人的用药提醒与用药计划操作。 */
export default function MedicationPlansPage() {
  const [plans, setPlans] = useState<MedicationPlan[]>([]);
  const [patientName, setPatientName] = useState('当前就诊人');
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState('');
  const [notice, setNotice] = useState('');
  const operationKeys = useRef<Record<string, string>>({});

  /** 依据“我的”专属就诊人选择读取用药计划。 */
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
      setPlans(await getMedicationPlans(patientId));
    } catch (requestError) {
      setNotice(getApiErrorMessage(requestError));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void loadPlans(); }, []);

  /** 调用后端状态机更新用药计划，网络重试复用同一动作幂等键。 */
  async function changePlan(plan: MedicationPlan, action: MedicationPlanAction) {
    const operationId = `${plan.id}:${action}`;
    setSubmitting(operationId);
    const key = operationKeys.current[operationId] || (operationKeys.current[operationId] = createIdempotencyKey());
    try {
      await updateMedicationPlan(plan.id, action, key);
      delete operationKeys.current[operationId];
      await loadPlans();
    } catch (requestError) {
      // 状态冲突后重新读取服务端状态，避免页面保留过期操作按钮。
      if (typeof requestError === 'object' && requestError !== null && 'status' in requestError && requestError.status === 409) {
        delete operationKeys.current[operationId];
        await loadPlans();
      }
      setNotice(getApiErrorMessage(requestError));
    } finally {
      setSubmitting('');
    }
  }

  return <main className="subpage"><PageHeader title="用药提醒" /><section className="subpage-content plan-page"><header className="plan-page__intro"><Pill size={25} /><div><h2>{patientName}的用药计划</h2><p>按计划服用并及时更新执行状态</p></div></header>
    {loading && <p className="empty-state">正在读取用药计划...</p>}
    {!loading && plans.map((plan) => {
      const reminderAction = getMedicationReminderAction(plan);
      const planActions = getMedicationPlanActions(plan.status);
      const reminderTimes = formatMedicationReminderTimes(plan.reminderTimes);
      return <article className="plan-card" key={plan.id}><div className="plan-card__top"><div><h3>{plan.drugName}</h3><p>{plan.dosage} · {plan.frequency}</p></div><em className={`status-tag ${plan.status.toLowerCase()}`}>{getMedicationPlanStatusText(plan.status)}</em></div><p className="plan-time"><Clock3 size={17} />下次提醒：{plan.nextReminderAt ? formatMedicalTime(plan.nextReminderAt) : '暂未设置'}</p>{plan.reminderEnabled && reminderTimes && <p className="plan-time plan-reminder-times"><Clock3 size={17} />提醒时刻：{reminderTimes}</p>}{(reminderAction || planActions.length > 0) && <div className="plan-actions">{reminderAction && <button className={reminderAction === 'DISABLE_REMINDER' ? 'secondary-button' : 'plan-action-button'} disabled={Boolean(submitting)} type="button" onClick={() => void changePlan(plan, reminderAction)}>{submitting === `${plan.id}:${reminderAction}` ? '处理中...' : getMedicationActionText(reminderAction)}</button>}{planActions.map((action) => <button className={action === 'COMPLETE' ? 'secondary-button' : 'plan-action-button'} disabled={Boolean(submitting)} key={action} type="button" onClick={() => void changePlan(plan, action)}>{submitting === `${plan.id}:${action}` ? '处理中...' : getMedicationActionText(action)}</button>)}</div>}</article>;
    })}
    {!loading && !plans.length && <p className="empty-state">暂无用药提醒</p>}
  </section>{notice && <div className="toast" role="status" onClick={() => setNotice('')}>{notice}</div>}</main>;
}
