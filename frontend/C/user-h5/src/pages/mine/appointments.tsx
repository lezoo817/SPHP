import { useEffect, useMemo, useState } from 'react';
import { CalendarDays, FileSearch, RefreshCw } from 'lucide-react';
import { useLocation } from 'umi';
import { Dialog } from '../../components/Dialog';
import { PageHeader } from '../../components/PageHeader';
import { getMinePatientId, resolveMinePatientId, saveMinePatientId } from '../../models/mine-patient';
import { getFamilyMembers } from '../../services/family';
import { getAppointments } from '../../services/registration';
import type { Appointment, FamilyMember } from '../../typings/api';
import { getApiErrorMessage } from '../../utils/form';
import { formatMedicalTime, getAppointmentStatusText } from '../../utils/medical';
import { filterAppointmentRecordsByDate, getRecentAppointmentRecordRange, isAppointmentRecordDateRangeValid, matchesAppointmentRecordTab, mergeAppointmentRecordPages, type AppointmentRecordDateRange, type AppointmentRecordTab } from '../../utils/appointment-record';

const APPOINTMENT_RECORD_PAGE_SIZE = 100;
const appointmentRecordTabs: Array<{ key: AppointmentRecordTab; label: string }> = [
  { key: 'COMPLETED', label: '已完成' },
  { key: 'INVALID', label: '已失效/已过期' },
];

/** 展示“我的”当前就诊人的完成与失效挂号记录，并支持日期筛选。 */
export default function MineAppointmentsPage() {
  const location = useLocation();
  const query = new URLSearchParams(location.search);
  const initialRange = useMemo(() => ({
    startDate: query.get('startDate') || getRecentAppointmentRecordRange(30).startDate,
    endDate: query.get('endDate') || getRecentAppointmentRecordRange(30).endDate,
  }), [location.search]);
  const [members, setMembers] = useState<FamilyMember[]>([]);
  const [patientId, setPatientId] = useState<number>();
  const [appointments, setAppointments] = useState<Appointment[]>([]);
  const [range, setRange] = useState<AppointmentRecordDateRange>(initialRange);
  const [tab, setTab] = useState<AppointmentRecordTab>('COMPLETED');
  const [pageNo, setPageNo] = useState(1);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [patientOpen, setPatientOpen] = useState(false);
  const [notice, setNotice] = useState('');
  const [rangeNotice, setRangeNotice] = useState('');

  /** 读取指定就诊人的挂号记录页，加载更多时保留已读取的数据。 */
  async function loadAppointments(targetPatientId: number, targetPage = 1, append = false) {
    setLoading(true);
    try {
      const page = await getAppointments(targetPatientId, undefined, APPOINTMENT_RECORD_PAGE_SIZE, targetPage);
      setAppointments((current) => append ? mergeAppointmentRecordPages(current, page.records) : page.records);
      setPageNo(page.pageNo);
      setTotal(page.total);
    } catch (requestError) {
      setNotice(getApiErrorMessage(requestError));
    } finally {
      setLoading(false);
    }
  }

  /** 恢复项目全局当前就诊人，深链接参数仅在未设置全局选择时作为回退。 */
  async function initializeAppointments() {
    setLoading(true);
    try {
      const nextMembers = await getFamilyMembers();
      setMembers(nextMembers);
      const patientIdFromUrl = Number(query.get('patientId')) || undefined;
      const nextPatientId = resolveMinePatientId(nextMembers, getMinePatientId() || patientIdFromUrl);
      if (!nextPatientId) {
        setNotice('暂无可查询的就诊人');
        setLoading(false);
        return;
      }
      // 进入记录页时同步全局选择，返回首页、助手和购药后保持同一就诊人。
      saveMinePatientId(nextPatientId);
      setPatientId(nextPatientId);
      await loadAppointments(nextPatientId);
    } catch (requestError) {
      setNotice(getApiErrorMessage(requestError));
      setLoading(false);
    }
  }

  useEffect(() => { void initializeAppointments(); }, []);

  /** 选择快捷日期范围，立即筛选当前已加载的就诊记录。 */
  function selectRecentRange(days: number) {
    setRange(getRecentAppointmentRecordRange(days));
    setRangeNotice('');
  }

  /** 更新手动日期并校验起止关系，非法范围不展示记录。 */
  function changeRange(nextRange: AppointmentRecordDateRange) {
    setRange(nextRange);
    setRangeNotice(isAppointmentRecordDateRangeValid(nextRange) ? '' : '开始日期不能晚于结束日期');
  }

  /** 切换全局就诊人后从第一页重新读取，避免短暂混合不同患者的记录。 */
  function selectPatient(nextPatientId: number) {
    saveMinePatientId(nextPatientId);
    setPatientId(nextPatientId);
    setPatientOpen(false);
    setAppointments([]);
    setPageNo(1);
    setTotal(0);
    void loadAppointments(nextPatientId);
  }

  const currentPatient = members.find((member) => member.patientId === patientId);
  const tabAppointments = appointments.filter((appointment) => matchesAppointmentRecordTab(appointment, tab));
  const filteredAppointments = filterAppointmentRecordsByDate(tabAppointments, range);
  const hasMore = appointments.length < total;
  const emptyText = tabAppointments.length
    ? '请调整日期范围后重试'
    : tab === 'COMPLETED' ? '当前就诊人暂无已完成挂号记录' : '当前就诊人暂无已失效或已过期挂号记录';

  return <main className="subpage report-page appointment-record-page"><PageHeader title="就诊记录" backPath="/mine" /><section className="subpage-content">
    <button className="assistant-patient mine-prescription-patient" type="button" onClick={() => setPatientOpen(true)}>就诊人 <b>{currentPatient?.name || '未选择'}</b><span>{currentPatient?.phone || ''}</span><b>切换 <RefreshCw size={18} /></b></button>
    <section className="report-filter-card"><div className="report-filter-card__title"><CalendarDays size={21} /><h2>挂号记录</h2></div><div className="report-date-row"><label>开始日期<input aria-label="挂号开始日期" type="date" value={range.startDate} onChange={(event) => changeRange({ ...range, startDate: event.target.value })} /></label><label>结束日期<input aria-label="挂号结束日期" type="date" value={range.endDate} onChange={(event) => changeRange({ ...range, endDate: event.target.value })} /></label></div><div className="report-quick-ranges">{[30, 90, 180].map((days) => <button className={range.startDate === getRecentAppointmentRecordRange(days).startDate && range.endDate === getRecentAppointmentRecordRange(days).endDate ? 'active' : ''} key={days} type="button" onClick={() => selectRecentRange(days)}>最近{days}天</button>)}</div>{rangeNotice && <p className="form-error">{rangeNotice}</p>}</section>
    <div className="appointment-record-tabs" role="tablist" aria-label="就诊记录分类">{appointmentRecordTabs.map((item) => <button aria-selected={tab === item.key} className={tab === item.key ? 'active' : ''} key={item.key} role="tab" type="button" onClick={() => setTab(item.key)}>{item.label}</button>)}</div>
    {loading && !appointments.length && <p className="empty-state">正在读取就诊记录...</p>}
    {!loading && !filteredAppointments.length && <section className="report-empty-state"><FileSearch size={58} /><h2>未查询到就诊记录</h2><p>{emptyText}</p></section>}
    {filteredAppointments.map((appointment) => <article className="appointment-record-card" key={appointment.id}><div><b>{formatMedicalTime(appointment.startTime)}</b><span>{appointment.departmentName || '科室待确认'} · {appointment.doctorName || '医生待确认'}</span><small>科室位置：{appointment.departmentLocation || '科室位置待确认'}</small></div><em className={`appointment-record-card__status status-${appointment.status.toLowerCase()}`}>{getAppointmentStatusText(appointment.status)}</em></article>)}
    {hasMore && <button className="load-more-button" disabled={loading || !patientId} type="button" onClick={() => patientId && void loadAppointments(patientId, pageNo + 1, true)}>{loading ? '加载中...' : '加载更多记录'}</button>}
  </section>{patientOpen && <Dialog title="切换就诊人" onClose={() => setPatientOpen(false)}>{members.map((member) => <button className="choice-row" key={member.patientId} type="button" onClick={() => selectPatient(member.patientId)}><span>{member.name}</span><small>{member.relationName || member.relation}{member.patientId === patientId ? ' · 当前选择' : ''}</small></button>)}{members.filter((member) => member.relation !== 'SELF').length === 0 && <p className="empty-state">当前用户未绑定亲属</p>}</Dialog>}{notice && <div className="toast" role="status" onClick={() => setNotice('')}>{notice}</div>}</main>;
}
