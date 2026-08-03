import { useEffect, useState } from 'react';
import { CalendarPlus, ChevronRight, ClipboardPlus, FileChartColumn, HeartPulse, Pill, Search, Stethoscope } from 'lucide-react';
import { useNavigate } from 'umi';
import { BottomTab } from '../../components/BottomTab';
import { Dialog } from '../../components/Dialog';
import { getSelection, saveSelection } from '../../models/selection';
import { getFamilyMembers } from '../../services/family';
import { getFollowUpPlans, getMedicationPlans } from '../../services/health';
import { getAppointments, getHospitals } from '../../services/registration';
import type { FamilyMember, Hospital } from '../../typings/api';
import { buildHealthTodos, type HealthTodo, type PatientHealthSource } from '../../utils/health-notification';
import { formatMedicalTime, sortHospitals } from '../../utils/medical';

/** 展示医院入口、就诊人、快捷服务和全账号健康待办的首页。 */
export default function HomePage() {
  const navigate = useNavigate();
  const [hospitals, setHospitals] = useState<Hospital[]>([]);
  const [members, setMembers] = useState<FamilyMember[]>([]);
  const [selected, setSelected] = useState(getSelection());
  const [patientOpen, setPatientOpen] = useState(false);
  const [notice, setNotice] = useState('');
  const [todos, setTodos] = useState<HealthTodo[]>([]);

  /** 读取单个就诊人的三类待办，供首页统一展示。 */
  async function loadPatientHealthSource(member: FamilyMember): Promise<PatientHealthSource> {
    const [appointmentPage, medicationPlans, followUps] = await Promise.all([
      getAppointments(member.patientId, undefined, 100),
      getMedicationPlans(member.patientId),
      getFollowUpPlans(member.patientId),
    ]);
    return { patientId: member.patientId, patientName: member.name, appointments: appointmentPage.records, medicationPlans, followUps };
  }

  /** 初始化医院、当前就诊人和账号全部健康待办。 */
  async function loadHome() {
    try {
      const [nextHospitals, nextMembers] = await Promise.all([getHospitals(), getFamilyMembers()]);
      setHospitals(sortHospitals(nextHospitals));
      setMembers(nextMembers);
      const state = getSelection();
      const next = {
        hospitalId: state.hospitalId || nextHospitals[0]?.hospitalId,
        patientId: state.patientId || nextMembers.find((item) => item.isDefault)?.patientId || nextMembers[0]?.patientId,
      };
      saveSelection(next);
      setSelected(next);
      // 后端按患者隔离待办，首页需要汇总本人和家属后才能展示账号全部待办。
      const healthResults = await Promise.allSettled(nextMembers.map(loadPatientHealthSource));
      const sources = healthResults.filter((item): item is PromiseFulfilledResult<PatientHealthSource> => item.status === 'fulfilled').map((item) => item.value);
      setTodos(buildHealthTodos(sources));
      if (healthResults.some((item) => item.status === 'rejected')) setNotice('部分健康待办加载失败，请稍后重试');
    } catch (error: unknown) {
      setNotice(error instanceof Error ? error.message : '首页数据加载失败');
    }
  }

  useEffect(() => { void loadHome(); }, []);

  /** 根据待办类别跳转到可继续处理的页面。 */
  function openTodo(todo: HealthTodo) {
    if (todo.type === 'APPOINTMENT') navigate('/assistant');
    else if (todo.type === 'MEDICATION') navigate('/mine/medication-plans');
    else navigate('/mine/follow-ups');
  }

  const currentHospital = hospitals.find((item) => item.hospitalId === selected.hospitalId);
  const currentPatient = members.find((item) => item.patientId === selected.patientId);
  const services = [
    { label: '预约挂号', icon: CalendarPlus, action: () => navigate('/home/departments') },
    { label: '智能导诊', icon: Stethoscope },
    { label: '在线问诊', icon: HeartPulse, action: () => navigate('/assistant') },
    { label: '处方购药', icon: Pill, action: () => navigate('/pharmacy') },
    { label: '报告查询', icon: FileChartColumn },
    { label: '用药提醒', icon: ClipboardPlus, action: () => navigate('/mine/medication-plans') },
  ];

  return <main className="home-page">
    <header className="home-hero"><div className="home-brand"><b>智</b><div><strong>智愈先锋</strong><span>省人民医院智慧医疗服务</span></div></div><p>让每一次就医，都更清晰、更安心</p></header>
    <section className="home-content">
      <button className="hospital-switch" type="button" onClick={() => navigate('/home/hospitals')}>当前医院：{currentHospital?.name || '选择医院'} <ChevronRight size={18} /></button>
      <button className="search-bar" type="button" onClick={() => navigate('/home/departments')}><Search size={24} /><span>搜索医生、科室</span></button>
      <section className="patient-switch-card"><div><b>当前就诊人 · {currentPatient?.name || '未选择'}</b><p>{currentPatient?.phone || '资料待完善'}</p></div><button type="button" className="text-button" onClick={() => setPatientOpen(true)}>切换 <ChevronRight size={19} /></button></section>
      <h2>快捷服务</h2>
      <section className="quick-grid">{services.map(({ label, icon: Icon, action }) => <button key={label} type="button" onClick={action || (() => setNotice(`${label}暂未开放`))}><Icon size={29} /><span>{label}</span></button>)}</section>
      <section className="todo-section"><div className="section-title"><h2>健康待办</h2>{todos.length > 0 && <span className="todo-count">{todos.length} 项待处理</span>}</div>
        {todos.map((todo) => <button className="health-todo-card" type="button" key={`${todo.type}-${todo.id}-${todo.patientId}`} onClick={() => openTodo(todo)}><div className={`health-todo-card__icon ${todo.type.toLowerCase()}`}>{todo.type === 'APPOINTMENT' ? '挂' : todo.type === 'MEDICATION' ? '药' : '访'}</div><div><b>{todo.occurredAt ? formatMedicalTime(todo.occurredAt) : '时间待确认'} · {todo.title}</b><span>{todo.patientName} · {todo.detail}</span></div><ChevronRight size={18} /></button>)}
        {!todos.length && <p className="empty-state">暂无健康待办</p>}
      </section>
    </section>
    {patientOpen && <Dialog title="切换就诊人" onClose={() => setPatientOpen(false)}>{members.map((item) => <button className="choice-row" key={item.patientId} type="button" onClick={() => { saveSelection({ patientId: item.patientId }); setSelected(getSelection()); setPatientOpen(false); }}><span>{item.name}</span><small>{item.relationName || item.relation}</small></button>)}{members.filter((item) => item.relation !== 'SELF').length === 0 && <p className="empty-state">当前用户未绑定亲属</p>}</Dialog>}
    {notice && <div className="toast" role="status" onClick={() => setNotice('')}>{notice}</div>}
    <BottomTab onUnavailable={() => setNotice('该页面暂未开放')} />
  </main>;
}
