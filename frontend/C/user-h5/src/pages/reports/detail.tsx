import { useEffect, useMemo, useState } from 'react';
import { FileText, Sparkles, Stethoscope } from 'lucide-react';
import { useLocation, useNavigate, useParams } from 'umi';
import { PageHeader } from '../../components/PageHeader';
import { getFamilyMembers } from '../../services/family';
import { getMedicalRecord } from '../../services/medical-record';
import type { MedicalRecordDetail } from '../../typings/api';
import { getApiErrorMessage } from '../../utils/form';
import { buildMedicalRecordInterpretationAgentState, createMedicalRecordDisplayNumber } from '../../utils/medical-record';

/** 展示医生开具的病历正文、受诊人资料与完成时间。 */
export default function MedicalRecordDetailPage() {
  const { consultId } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const [detail, setDetail] = useState<MedicalRecordDetail>();
  const [patientName, setPatientName] = useState<string>();
  const [loading, setLoading] = useState(true);
  const [notice, setNotice] = useState('');

  /** 同步读取病历正文和受诊人姓名；成员读取失败不影响病历正文查看。 */
  async function loadMedicalRecordDetail() {
    const id = Number(consultId);
    if (!Number.isInteger(id) || id <= 0) {
      setNotice('病历编号不正确');
      setLoading(false);
      return;
    }
    // 路由参数变化时先清理旧病历，避免短暂显示上一份就诊数据。
    setDetail(undefined);
    setPatientName(undefined);
    setNotice('');
    setLoading(true);
    const [detailResult, membersResult] = await Promise.allSettled([getMedicalRecord(id), getFamilyMembers()]);
    if (detailResult.status === 'fulfilled') {
      setDetail(detailResult.value);
      // 病历详情只返回 patientId，成员接口负责提供可展示的受诊人姓名。
      if (membersResult.status === 'fulfilled') setPatientName(membersResult.value.find((member) => member.patientId === detailResult.value.patientId)?.name);
    } else {
      setNotice(getApiErrorMessage(detailResult.reason));
    }
    setLoading(false);
  }

  useEffect(() => { void loadMedicalRecordDetail(); }, [consultId]);

  // 随机尾号只在当前病历详情生命周期内生成一次，避免页面重渲染时编号变化。
  const reportNumber = useMemo(() => createMedicalRecordDisplayNumber(detail?.completedAt), [detail?.id, detail?.completedAt]);

  /** 跳转至独立 AI 会话，由 Agent 按病历编号权威读取内容。 */
  function interpretWithAi() {
    const id = Number(consultId);
    if (!detail || !Number.isInteger(id) || id <= 0) return;
    // 路由状态仅传递来源地址和业务编号，禁止携带病历正文等敏感信息。
    navigate('/agent', {
      state: buildMedicalRecordInterpretationAgentState(`${location.pathname}${location.search}`, id),
    });
  }

  return <main className="subpage report-detail-page"><PageHeader title="病历详情" backPath={`/medical-records${location.search}`} /><section className="subpage-content">
    {loading && <p className="empty-state">正在读取病历详情...</p>}
    {!loading && detail && <><header className="report-detail-title"><FileText size={28} /><div><h2>{detail.departmentName || '医生病历'}</h2><p>{detail.doctorName || '医生待确认'} · 完成于 {formatMedicalRecordDate(detail.completedAt)}</p></div></header><section className="report-document"><div className="report-document__header"><Stethoscope size={23} /><div><b>病历报告</b><span>报告编号：{reportNumber}</span></div></div><dl className="report-document__meta"><div><dt>所属科室</dt><dd>{detail.departmentName || '暂未提供'}</dd></div><div><dt>接诊医生</dt><dd>{detail.doctorName || '暂未提供'}</dd></div><div><dt>开始时间</dt><dd>{formatMedicalRecordDate(detail.startedAt)}</dd></div><div><dt>完成时间</dt><dd>{formatMedicalRecordDate(detail.completedAt)}</dd></div><div><dt>受诊人</dt><dd>{patientName || '暂未提供'}</dd></div></dl><section className="report-document__content"><h3>病历内容</h3><p>{detail.doctorNote}</p></section><p className="report-document__updated">最后更新：{formatMedicalRecordDate(detail.updatedAt)}</p></section><button type="button" className="report-detail-page__ai-button" onClick={interpretWithAi} disabled={!Number.isInteger(Number(consultId)) || Number(consultId) <= 0}><Sparkles size={18} />AI 一键解读</button></>}
  </section>{notice && <div className="toast" role="status" onClick={() => setNotice('')}>{notice}</div>}</main>;
}

/** 将后端时间转换为病历详情的患者可读时间。 */
function formatMedicalRecordDate(value?: string): string {
  return value ? new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(new Date(value)) : '时间待确认';
}
