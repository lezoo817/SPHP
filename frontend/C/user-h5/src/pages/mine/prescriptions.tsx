import { useEffect, useMemo, useState } from 'react';
import { CalendarDays, ChevronRight, FileSearch, RefreshCw } from 'lucide-react';
import { useLocation, useNavigate } from 'umi';
import { Dialog } from '../../components/Dialog';
import { PageHeader } from '../../components/PageHeader';
import { getMinePatientId, resolveMinePatientId, saveMinePatientId } from '../../models/mine-patient';
import { getPrescriptions } from '../../services/consultation';
import { getFamilyMembers } from '../../services/family';
import type { FamilyMember, Prescription } from '../../typings/api';
import { getApiErrorMessage } from '../../utils/form';
import { buildPharmacyHomePath } from '../../utils/pharmacy';
import { buildMinePrescriptionDetailPath, filterPrescriptionsByDate, formatPrescriptionIssuedAt, getPrescriptionDisplayNumber, getRecentPrescriptionRange, isPrescriptionDateRangeValid, mergePrescriptionPages, type PrescriptionDateRange } from '../../utils/prescription';

const PRESCRIPTION_PAGE_SIZE = 100;

/** 展示“我的”入口当前就诊人的已批准处方、日期筛选与分页结果。 */
export default function MinePrescriptionsPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const query = new URLSearchParams(location.search);
  const entrySource = query.get('entrySource') === 'pharmacy' ? 'pharmacy' : undefined;
  const initialRange = useMemo(() => ({
    startDate: query.get('startDate') || getRecentPrescriptionRange(30).startDate,
    endDate: query.get('endDate') || getRecentPrescriptionRange(30).endDate,
  }), [location.search]);
  const [members, setMembers] = useState<FamilyMember[]>([]);
  const [patientId, setPatientId] = useState<number>();
  const [prescriptions, setPrescriptions] = useState<Prescription[]>([]);
  const [range, setRange] = useState<PrescriptionDateRange>(initialRange);
  const [pageNo, setPageNo] = useState(1);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [patientOpen, setPatientOpen] = useState(false);
  const [notice, setNotice] = useState('');
  const [rangeNotice, setRangeNotice] = useState('');

  /** 读取指定就诊人的处方页，加载更多时保留已经获得的处方。 */
  async function loadPrescriptions(targetPatientId: number, targetPage = 1, append = false) {
    setLoading(true);
    try {
      const page = await getPrescriptions({ patientId: targetPatientId, pageNo: targetPage, pageSize: PRESCRIPTION_PAGE_SIZE });
      setPrescriptions((current) => append ? mergePrescriptionPages(current, page.records) : page.records);
      setPageNo(page.pageNo);
      setTotal(page.total);
    } catch (requestError) {
      setNotice(getApiErrorMessage(requestError));
    } finally {
      setLoading(false);
    }
  }

  /** 恢复项目全局当前就诊人；地址栏患者参数仅作为无全局选择时的回退。 */
  async function initializePrescriptions() {
    setLoading(true);
    try {
      const nextMembers = await getFamilyMembers();
      setMembers(nextMembers);
      // 全局选择已存在时优先使用，避免详情页旧地址栏参数覆盖用户最近的切换。
      const patientIdFromUrl = Number(query.get('patientId')) || undefined;
      const nextPatientId = resolveMinePatientId(nextMembers, getMinePatientId() || patientIdFromUrl);
      if (!nextPatientId) {
        setNotice('暂无可查询的就诊人');
        setLoading(false);
        return;
      }
      saveMinePatientId(nextPatientId);
      setPatientId(nextPatientId);
      await loadPrescriptions(nextPatientId);
    } catch (requestError) {
      setNotice(getApiErrorMessage(requestError));
      setLoading(false);
    }
  }

  useEffect(() => { void initializePrescriptions(); }, []);

  /** 选择快捷日期范围，立即筛选当前已加载的处方。 */
  function selectRecentRange(days: number) {
    setRange(getRecentPrescriptionRange(days));
    setRangeNotice('');
  }

  /** 更新手动日期时校验起止关系，合法后即时筛选当前列表。 */
  function changeRange(nextRange: PrescriptionDateRange) {
    setRange(nextRange);
    setRangeNotice(isPrescriptionDateRangeValid(nextRange) ? '' : '开始日期不能晚于结束日期');
  }

  /** 切换全局就诊人并从第一页重新查询，防止混合不同患者处方。 */
  function selectPatient(nextPatientId: number) {
    saveMinePatientId(nextPatientId);
    setPatientId(nextPatientId);
    setPatientOpen(false);
    setPrescriptions([]);
    void loadPrescriptions(nextPatientId);
  }

  /** 携带当前筛选条件进入既有处方详情，详情返回时可还原列表上下文。 */
  function openPrescription(prescription: Prescription) {
    navigate(buildMinePrescriptionDetailPath(prescription.id, patientId, range, prescription.issuedAt, entrySource));
  }

  const currentPatient = members.find((member) => member.patientId === patientId);
  const filteredPrescriptions = filterPrescriptionsByDate(prescriptions, range);
  const hasMore = prescriptions.length < total;

  // 仅从购药进入时回到购药；“我的”入口仍保持原有返回逻辑。
  const backPath = entrySource === 'pharmacy' ? buildPharmacyHomePath(patientId) : '/mine';

  return <main className="subpage report-page prescription-query-page"><PageHeader title="我的处方" backPath={backPath} /><section className="subpage-content">
    <button className="assistant-patient mine-prescription-patient" type="button" onClick={() => setPatientOpen(true)}>就诊人 <b>{currentPatient?.name || '未选择'}</b><span>{currentPatient?.phone || ''}</span><b>切换 <RefreshCw size={18} /></b></button>
    <section className="report-filter-card"><div className="report-filter-card__title"><CalendarDays size={21} /><h2>全部处方</h2></div><div className="report-date-row"><label>开始日期<input aria-label="处方开始日期" type="date" value={range.startDate} onChange={(event) => changeRange({ ...range, startDate: event.target.value })} /></label><label>结束日期<input aria-label="处方结束日期" type="date" value={range.endDate} onChange={(event) => changeRange({ ...range, endDate: event.target.value })} /></label></div><div className="report-quick-ranges">{[30, 90, 180].map((days) => <button className={range.startDate === getRecentPrescriptionRange(days).startDate && range.endDate === getRecentPrescriptionRange(days).endDate ? 'active' : ''} key={days} type="button" onClick={() => selectRecentRange(days)}>最近{days}天</button>)}</div>{rangeNotice && <p className="form-error">{rangeNotice}</p>}</section>
    {loading && !prescriptions.length && <p className="empty-state">正在读取处方...</p>}
    {!loading && !filteredPrescriptions.length && <section className="report-empty-state"><FileSearch size={58} /><h2>未查询到处方</h2><p>{prescriptions.length ? '请调整日期范围后重试' : '当前就诊人暂无已批准处方'}</p></section>}
    {filteredPrescriptions.map((prescription) => <button className="report-list-card" key={prescription.id} type="button" onClick={() => openPrescription(prescription)}><div><h2>{prescription.doctorName || '医生待确认'}电子处方</h2><p>已批准 · 开具于 {formatPrescriptionIssuedAt(prescription.issuedAt)}</p><small>处方编号：{getPrescriptionDisplayNumber(prescription.id, prescription.issuedAt)}</small></div><ChevronRight size={20} /></button>)}
    {hasMore && <button className="load-more-button" disabled={loading || !patientId} type="button" onClick={() => patientId && void loadPrescriptions(patientId, pageNo + 1, true)}>{loading ? '加载中...' : '加载更多处方'}</button>}
  </section>{patientOpen && <Dialog title="切换就诊人" onClose={() => setPatientOpen(false)}>{members.map((member) => <button className="choice-row" key={member.patientId} type="button" onClick={() => selectPatient(member.patientId)}><span>{member.name}</span><small>{member.relationName || member.relation}{member.patientId === patientId ? ' · 当前选择' : ''}</small></button>)}{members.filter((member) => member.relation !== 'SELF').length === 0 && <p className="empty-state">当前用户未绑定亲属</p>}</Dialog>}{notice && <div className="toast" role="status" onClick={() => setNotice('')}>{notice}</div>}</main>;
}
