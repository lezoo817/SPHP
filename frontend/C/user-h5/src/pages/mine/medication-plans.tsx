import { useEffect, useMemo, useRef, useState } from 'react';
import { Clock3, Pill, RefreshCw } from 'lucide-react';
import { useLocation } from 'umi';
import { Dialog } from '../../components/Dialog';
import { PageHeader } from '../../components/PageHeader';
import { getFamilyMembers } from '../../services/family';
import { getMedicationPlans, updateMedicationPlan } from '../../services/health';
import { getMinePatientId, resolveMinePatientId, saveMinePatientId } from '../../models/mine-patient';
import type { FamilyMember, MedicationPlan, MedicationPlanAction } from '../../typings/api';
import { getApiErrorMessage, createIdempotencyKey } from '../../utils/form';
import { filterMedicationPlansByTab, formatMedicationReminderTimes, getMedicationActionText, getMedicationPlanActions, getMedicationPlanStatusText, getMedicationReminderAction, type MedicationPlanTab } from '../../utils/health-notification';
import { formatMedicalTime } from '../../utils/medical';

/** 展示当前“我的”就诊人的用药提醒与用药计划操作。 */
export default function MedicationPlansPage() {
  const [plans, setPlans] = useState<MedicationPlan[]>([]);
  const [members, setMembers] = useState<FamilyMember[]>([]);
  const [patientId, setPatientId] = useState<number>();
  const [patientName, setPatientName] = useState('当前就诊人');
  const [patientPhone, setPatientPhone] = useState('');
  const [patientOpen, setPatientOpen] = useState(false);
  const [planTab, setPlanTab] = useState<MedicationPlanTab>('IN_PROGRESS');
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState('');
  const [notice, setNotice] = useState('');
  const operationKeys = useRef<Record<string, string>>({});
  const location = useLocation();
  const patientIdFromUrl = useMemo(() => Number(new URLSearchParams(location.search).get('patientId')) || undefined, [location.search]);
  const displayedPlans = useMemo(() => filterMedicationPlansByTab(plans, planTab), [plans, planTab]);

  /** 依据页面选择、健康待办来源或“我的”专属选择读取用药计划。 */
  async function loadPlans(preferredPatientId?: number) {
    setLoading(true);
    try {
      const nextMembers = await getFamilyMembers();
      setMembers(nextMembers);
      const candidatePatientId = preferredPatientId || patientId || patientIdFromUrl || getMinePatientId();
      const resolvedPatientId = nextMembers.some((item) => item.patientId === candidatePatientId)
        ? candidatePatientId
        : resolveMinePatientId(nextMembers, getMinePatientId());
      if (!resolvedPatientId) {
        setPlans([]);
        setNotice('暂无可查询的就诊人');
        return;
      }
      // 切换后同步“我的”专属选择，其他业务页面选择不受影响。
      saveMinePatientId(resolvedPatientId);
      const selectedMember = nextMembers.find((item) => item.patientId === resolvedPatientId);
      setPatientId(resolvedPatientId);
      setPatientName(selectedMember?.name || '当前就诊人');
      setPatientPhone(selectedMember?.phone || '');
      setPlans(await getMedicationPlans(resolvedPatientId));
    } catch (requestError) {
      setNotice(getApiErrorMessage(requestError));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void loadPlans(patientIdFromUrl); }, [patientIdFromUrl]);

  /** 切换页面内就诊人并重新读取对应的用药计划。 */
  function selectPatient(nextPatientId: number) {
    setPatientOpen(false);
    setPlans([]);
    void loadPlans(nextPatientId);
  }

  /** 切换计划分类，仅筛选当前已加载的同一就诊人数据。 */
  function selectPlanTab(nextTab: MedicationPlanTab) {
    setPlanTab(nextTab);
  }

  /** 调用后端状态机更新用药计划，网络重试复用同一动作幂等键。 */
  async function changePlan(plan: MedicationPlan, action: MedicationPlanAction) {
    const operationId = `${plan.id}:${action}`;
    setSubmitting(operationId);
    const key = operationKeys.current[operationId] || (operationKeys.current[operationId] = createIdempotencyKey());
    try {
      await updateMedicationPlan(plan.id, action, key);
      delete operationKeys.current[operationId];
      await loadPlans(patientId);
    } catch (requestError) {
      // 状态冲突后重新读取服务端状态，避免页面保留过期操作按钮。
      if (typeof requestError === 'object' && requestError !== null && 'status' in requestError && requestError.status === 409) {
        delete operationKeys.current[operationId];
        await loadPlans(patientId);
      }
      setNotice(getApiErrorMessage(requestError));
    } finally {
      setSubmitting('');
    }
  }

  return <main className="subpage"><PageHeader title="用药提醒" /><section className="subpage-content plan-page"><button className="plan-page__intro plan-page__patient-switch" type="button" onClick={() => setPatientOpen(true)}><Pill size={25} /><div><h2>切换就诊人</h2><p>{patientName}{patientPhone ? ` · ${patientPhone}` : ''}</p></div><RefreshCw size={20} /></button>
    <div className="medication-plan-tabs" role="tablist" aria-label="用药计划状态"><button className={planTab === 'IN_PROGRESS' ? 'active' : ''} type="button" role="tab" aria-selected={planTab === 'IN_PROGRESS'} onClick={() => selectPlanTab('IN_PROGRESS')}>执行中</button><button className={planTab === 'COMPLETED' ? 'active' : ''} type="button" role="tab" aria-selected={planTab === 'COMPLETED'} onClick={() => selectPlanTab('COMPLETED')}>已完成</button></div>
    {loading && <p className="empty-state">正在读取用药计划...</p>}
    {!loading && displayedPlans.map((plan) => {
      const reminderAction = getMedicationReminderAction(plan);
      const planActions = getMedicationPlanActions(plan.status);
      const reminderTimes = formatMedicationReminderTimes(plan.reminderTimes);
      return <article className="plan-card" key={plan.id}><div className="plan-card__top"><div><h3>{plan.drugName}</h3><p>{plan.dosage} · {plan.frequency}</p></div><em className={`status-tag ${plan.status.toLowerCase()}`}>{getMedicationPlanStatusText(plan.status)}</em></div><p className="plan-time"><Clock3 size={17} />下次提醒：{plan.nextReminderAt ? formatMedicalTime(plan.nextReminderAt) : '暂未设置'}</p>{plan.reminderEnabled && reminderTimes && <p className="plan-time plan-reminder-times"><Clock3 size={17} />提醒时刻：{reminderTimes}</p>}{(reminderAction || planActions.length > 0) && <div className="plan-actions">{reminderAction && <button className={reminderAction === 'DISABLE_REMINDER' ? 'secondary-button' : 'plan-action-button'} disabled={Boolean(submitting)} type="button" onClick={() => void changePlan(plan, reminderAction)}>{submitting === `${plan.id}:${reminderAction}` ? '处理中...' : getMedicationActionText(reminderAction)}</button>}{planActions.map((action) => <button className={action === 'COMPLETE' ? 'secondary-button' : 'plan-action-button'} disabled={Boolean(submitting)} key={action} type="button" onClick={() => void changePlan(plan, action)}>{submitting === `${plan.id}:${action}` ? '处理中...' : getMedicationActionText(action)}</button>)}</div>}</article>;
    })}
    {!loading && !displayedPlans.length && <p className="empty-state">{planTab === 'COMPLETED' ? '暂无已完成的用药计划' : '暂无执行中的用药计划'}</p>}
  </section>{patientOpen && <Dialog title="切换就诊人" onClose={() => setPatientOpen(false)}>{members.map((member) => <button className="choice-row" key={member.patientId} type="button" onClick={() => selectPatient(member.patientId)}><span>{member.name}</span><small>{member.relationName || member.relation}{member.patientId === patientId ? ' · 当前选择' : ''}</small></button>)}</Dialog>}{notice && <div className="toast" role="status" onClick={() => setNotice('')}>{notice}</div>}</main>;
}
