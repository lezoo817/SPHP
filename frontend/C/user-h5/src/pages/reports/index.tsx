import { useEffect, useMemo, useState } from 'react';
import { CalendarDays, ChevronRight, FileSearch, RefreshCw } from 'lucide-react';
import { useLocation, useNavigate } from 'umi';
import { Dialog } from '../../components/Dialog';
import { PageHeader } from '../../components/PageHeader';
import { getMinePatientId } from '../../models/mine-patient';
import { getSelection, resolveSelfPatientId } from '../../models/selection';
import { getFamilyMembers } from '../../services/family';
import { getMedicalRecords } from '../../services/medical-record';
import type { FamilyMember, MedicalRecordItem } from '../../typings/api';
import { getApiErrorMessage } from '../../utils/form';
import { filterMedicalRecordsByDate, getRecentMedicalRecordRange, isMedicalRecordDateRangeValid, mergeMedicalRecordPages, type MedicalRecordDateRange } from '../../utils/medical-record';

const MEDICAL_RECORD_PAGE_SIZE = 100;

/** 展示当前入口就诊人的病历报告、日期筛选与分页结果。 */
export default function MedicalRecordsPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const query = new URLSearchParams(location.search);
  const source = query.get('source') === 'home' ? 'home' : 'mine';
  const initialRange = useMemo(() => ({
    startDate: query.get('startDate') || getRecentMedicalRecordRange(30).startDate,
    endDate: query.get('endDate') || getRecentMedicalRecordRange(30).endDate,
  }), [location.search]);
  const [members, setMembers] = useState<FamilyMember[]>([]);
  const [patientId, setPatientId] = useState<number>();
  const [medicalRecords, setMedicalRecords] = useState<MedicalRecordItem[]>([]);
  const [range, setRange] = useState<MedicalRecordDateRange>(initialRange);
  const [pageNo, setPageNo] = useState(1);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [patientOpen, setPatientOpen] = useState(false);
  const [notice, setNotice] = useState('');
  const [rangeNotice, setRangeNotice] = useState('');

  /** 读取指定就诊人的病历页，加载更多时保留已获取的服务端记录。 */
  async function loadMedicalRecords(targetPatientId: number, targetPage = 1, append = false) {
    setLoading(true);
    try {
      const page = await getMedicalRecords({ patientId: targetPatientId, pageNo: targetPage, pageSize: MEDICAL_RECORD_PAGE_SIZE });
      setMedicalRecords((current) => append ? mergeMedicalRecordPages(current, page.records) : page.records);
      setPageNo(page.pageNo);
      setTotal(page.total);
    } catch (requestError) {
      setNotice(getApiErrorMessage(requestError));
    } finally {
      setLoading(false);
    }
  }

  /** 根据来源恢复初始就诊人；病历页的临时切换不写回来源页面状态。 */
  async function initializeMedicalRecords() {
    setLoading(true);
    try {
      const nextMembers = await getFamilyMembers();
      setMembers(nextMembers);
      // 从详情页返回时优先恢复 URL 中的当前病历就诊人，再回退到入口页选择。
      const patientIdFromUrl = Number(query.get('patientId')) || undefined;
      const preferredPatientId = patientIdFromUrl || (source === 'home' ? getSelection().patientId : getMinePatientId());
      const nextPatientId = nextMembers.some((member) => member.patientId === preferredPatientId)
        ? preferredPatientId
        : resolveSelfPatientId(nextMembers);
      if (!nextPatientId) {
        setNotice('暂无可查询的就诊人');
        setLoading(false);
        return;
      }
      setPatientId(nextPatientId);
      await loadMedicalRecords(nextPatientId);
    } catch (requestError) {
      setNotice(getApiErrorMessage(requestError));
      setLoading(false);
    }
  }

  useEffect(() => { void initializeMedicalRecords(); }, [source]);

  /** 选择快捷日期范围，立即在已加载记录中更新查询结果。 */
  function selectRecentRange(days: number) {
    setRange(getRecentMedicalRecordRange(days));
    setRangeNotice('');
  }

  /** 更新手动日期时校验起止关系，日期合法后即时筛选当前列表。 */
  function changeRange(nextRange: MedicalRecordDateRange) {
    setRange(nextRange);
    setRangeNotice(isMedicalRecordDateRangeValid(nextRange) ? '' : '开始日期不能晚于结束日期');
  }

  /** 切换病历页内就诊人并重新从第一页读取其病历，避免混合不同患者数据。 */
  function selectPatient(nextPatientId: number) {
    setPatientId(nextPatientId);
    setPatientOpen(false);
    setMedicalRecords([]);
    void loadMedicalRecords(nextPatientId);
  }

  /** 保留当前来源、就诊人和日期范围进入病历详情，便于返回原筛选结果。 */
  function openMedicalRecord(consultId: number) {
    const params = new URLSearchParams({ source, ...(patientId ? { patientId: String(patientId) } : {}), startDate: range.startDate, endDate: range.endDate });
    navigate(`/medical-records/${consultId}?${params.toString()}`);
  }

  const currentPatient = members.find((member) => member.patientId === patientId);
  const filteredMedicalRecords = filterMedicalRecordsByDate(medicalRecords, range);
  const hasMore = medicalRecords.length < total;
  const backPath = source === 'home' ? '/home' : '/mine';

  return <main className="subpage report-page"><PageHeader title="病历报告" backPath={backPath} /><section className="subpage-content">
    <button className="report-patient-card" type="button" onClick={() => setPatientOpen(true)}><span><b>{currentPatient?.name || '当前就诊人'}</b><small>{currentPatient?.relationName || currentPatient?.relation || '就诊人'}</small></span><em>切换就诊人 <RefreshCw size={18} /></em></button>
    <section className="report-filter-card"><div className="report-filter-card__title"><CalendarDays size={21} /><h2>全部病历</h2></div><div className="report-date-row"><label>开始日期<input aria-label="病历开始日期" type="date" value={range.startDate} onChange={(event) => changeRange({ ...range, startDate: event.target.value })} /></label><label>结束日期<input aria-label="病历结束日期" type="date" value={range.endDate} onChange={(event) => changeRange({ ...range, endDate: event.target.value })} /></label></div><div className="report-quick-ranges">{[30, 90, 180].map((days) => <button className={range.startDate === getRecentMedicalRecordRange(days).startDate && range.endDate === getRecentMedicalRecordRange(days).endDate ? 'active' : ''} key={days} type="button" onClick={() => selectRecentRange(days)}>最近{days}天</button>)}</div>{rangeNotice && <p className="form-error">{rangeNotice}</p>}</section>
    {loading && !medicalRecords.length && <p className="empty-state">正在读取病历...</p>}
    {!loading && !filteredMedicalRecords.length && <section className="report-empty-state"><FileSearch size={58} /><h2>未查询到病历</h2><p>{medicalRecords.length ? '请调整日期范围后重试' : '当前就诊人暂无已完成的医生病历'}</p></section>}
    {filteredMedicalRecords.map((medicalRecord) => <button className="report-list-card" key={medicalRecord.id} type="button" onClick={() => openMedicalRecord(medicalRecord.id)}><div><h2>{medicalRecord.departmentName || '科室待确认'}病历</h2><p>{medicalRecord.doctorName || '医生待确认'} · 完成于 {formatMedicalRecordDate(medicalRecord.completedAt)}</p><small>最后更新：{formatMedicalRecordDate(medicalRecord.updatedAt)}</small></div><ChevronRight size={20} /></button>)}
    {hasMore && <button className="load-more-button" disabled={loading || !patientId} type="button" onClick={() => patientId && void loadMedicalRecords(patientId, pageNo + 1, true)}>{loading ? '加载中...' : '加载更多病历'}</button>}
  </section>{patientOpen && <Dialog title="切换就诊人" onClose={() => setPatientOpen(false)}>{members.map((member) => <button className="choice-row" key={member.patientId} type="button" onClick={() => selectPatient(member.patientId)}><span>{member.name}</span><small>{member.relationName || member.relation}{member.patientId === patientId ? ' · 当前选择' : ''}</small></button>)}{members.filter((member) => member.relation !== 'SELF').length === 0 && <p className="empty-state">当前用户未绑定亲属</p>}</Dialog>}{notice && <div className="toast" role="status" onClick={() => setNotice('')}>{notice}</div>}</main>;
}

/** 将后端时间转换为病历列表中简明的年月日时间。 */
function formatMedicalRecordDate(value?: string): string {
  return value ? new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(new Date(value)) : '时间待确认';
}
