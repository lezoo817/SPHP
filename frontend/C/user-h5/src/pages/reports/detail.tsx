import { useEffect, useMemo, useState } from 'react';
import { AlertCircle, FileText, Sparkles, Stethoscope } from 'lucide-react';
import { useLocation, useParams } from 'umi';
import { PageHeader } from '../../components/PageHeader';
import { getFamilyMembers } from '../../services/family';
import { getReport, getReportInterpretation } from '../../services/report';
import type { ReportDetail, ReportInterpretation } from '../../typings/api';
import { getApiErrorMessage } from '../../utils/form';
import { createReportDisplayNumber, isReportInterpretationPending } from '../../utils/report';

/** 展示检查报告正文、受诊人资料和已生成的报告解读。 */
export default function ReportDetailPage() {
  const { reportId } = useParams();
  const location = useLocation();
  const [detail, setDetail] = useState<ReportDetail>();
  const [patientName, setPatientName] = useState<string>();
  const [interpretation, setInterpretation] = useState<ReportInterpretation>();
  const [isInterpretationPending, setIsInterpretationPending] = useState(false);
  const [loading, setLoading] = useState(true);
  const [notice, setNotice] = useState('');

  /** 同步读取报告正文与解读；解读未完成不影响正文展示。 */
  async function loadReportDetail() {
    const id = Number(reportId);
    if (!Number.isInteger(id) || id <= 0) {
      setNotice('报告编号不正确');
      setLoading(false);
      return;
    }
    // 路由参数变化时先清理旧报告，避免短暂显示上一份病历或解读。
    setDetail(undefined);
    setPatientName(undefined);
    setInterpretation(undefined);
    setIsInterpretationPending(false);
    setNotice('');
    setLoading(true);
    const [detailResult, interpretationResult, membersResult] = await Promise.allSettled([getReport(id), getReportInterpretation(id), getFamilyMembers()]);
    if (detailResult.status === 'fulfilled') {
      setDetail(detailResult.value);
      // 报告详情只返回 patientId，成员接口负责提供可展示的受诊人姓名。
      if (membersResult.status === 'fulfilled') setPatientName(membersResult.value.find((member) => member.patientId === detailResult.value.patientId)?.name);
    }
    else setNotice(getApiErrorMessage(detailResult.reason));
    if (interpretationResult.status === 'fulfilled') setInterpretation(interpretationResult.value);
    else if (isReportInterpretationPending(interpretationResult.reason)) setIsInterpretationPending(true);
    else if (detailResult.status === 'fulfilled') setNotice(getApiErrorMessage(interpretationResult.reason));
    setLoading(false);
  }

  useEffect(() => { void loadReportDetail(); }, [reportId]);

  // 随机尾号只在当前报告详情生命周期内生成一次，避免页面重渲染时编号变化。
  const reportNumber = useMemo(() => createReportDisplayNumber(detail?.completedAt), [detail?.id, detail?.completedAt]);

  return <main className="subpage report-detail-page"><PageHeader title="报告详情" backPath={`/reports${location.search}`} /><section className="subpage-content">
    {loading && <p className="empty-state">正在读取报告详情...</p>}
    {!loading && detail && <><header className="report-detail-title"><FileText size={28} /><div><h2>{detail.departmentName || '医生报告'}</h2><p>{detail.doctorName || '医生待确认'} · 完成于 {formatReportDate(detail.completedAt)}</p></div></header><section className="report-document"><div className="report-document__header"><Stethoscope size={23} /><div><b>检查报告</b><span>报告编号：{reportNumber}</span></div></div><dl className="report-document__meta"><div><dt>所属科室</dt><dd>{detail.departmentName || '暂未提供'}</dd></div><div><dt>接诊医生</dt><dd>{detail.doctorName || '暂未提供'}</dd></div><div><dt>开始时间</dt><dd>{formatReportDate(detail.startedAt)}</dd></div><div><dt>完成时间</dt><dd>{formatReportDate(detail.completedAt)}</dd></div><div><dt>受诊人</dt><dd>{patientName || '暂未提供'}</dd></div></dl><section className="report-document__content"><h3>医生病历正文</h3><p>{detail.doctorNote}</p></section><p className="report-document__updated">最后更新：{formatReportDate(detail.updatedAt)}</p></section>{interpretation && <section className="report-interpretation"><div><Sparkles size={22} /><h2>报告解读</h2></div><p>{interpretation.content}</p><small>{interpretation.disclaimer}</small><time>生成时间：{formatReportDate(interpretation.generatedAt)}</time></section>}{isInterpretationPending && <section className="report-interpretation pending"><AlertCircle size={22} /><div><h2>报告解读正在生成</h2><p>医生解读准备完成后会在此处显示。</p></div></section>}</>}
  </section>{notice && <div className="toast" role="status" onClick={() => setNotice('')}>{notice}</div>}</main>;
}

/** 将后端时间转换为详情页的患者可读时间。 */
function formatReportDate(value?: string): string {
  return value ? new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(new Date(value)) : '时间待确认';
}
