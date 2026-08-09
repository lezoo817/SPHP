import { useEffect, useState } from 'react';
import { ChevronRight } from 'lucide-react';
import { useLocation, useNavigate, useParams } from 'umi';
import { PageHeader } from '../../components/PageHeader';
import { getSelection } from '../../models/selection';
import { getConsultation } from '../../services/consultation';
import type { ConsultationDetail } from '../../typings/api';
import { getApiErrorMessage } from '../../utils/form';
import { buildAssistantPrescriptionDetailPath } from '../../utils/prescription';

/** 展示在线问诊的 AI 预问诊、医生单向回复和已批准处方。 */
export default function ConsultationPage() {
  const { consultationId } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const [detail, setDetail] = useState<ConsultationDetail>();
  const [notice, setNotice] = useState('');
  const patientIdFromUrl = Number(new URLSearchParams(location.search).get('patientId')) || undefined;

  /** 读取问诊详情，通知跳转后可直接看到医生最新回复。 */
  async function load() {
    try {
      setDetail(await getConsultation(Number(consultationId)));
    } catch (error) {
      setNotice(getApiErrorMessage(error));
    }
  }

  useEffect(() => { void load(); }, [consultationId]);

  const statusText = detail?.status === 'PENDING' ? '等待医生回复' : detail?.status === 'IN_PROGRESS' ? '医生接诊中' : detail?.status === 'COMPLETED' ? '问诊已完成' : '问诊已结束';
  const patientId = detail?.patientId || patientIdFromUrl || getSelection().patientId;
  return <main className="subpage"><PageHeader title="在线问诊" backPath="/assistant" /><section className="subpage-content chat-page">
    <h2>{detail?.doctor.name || '在线问诊'}</h2>
    <p>{statusText}</p>
    {detail?.preConsultation && <section className="record-card"><h3>AI 预问诊摘要</h3><p><b>主诉：</b>{detail.preConsultation.chiefComplaint}</p>{detail.preConsultation.historyOfPresentIllness && <p><b>现病史：</b>{detail.preConsultation.historyOfPresentIllness}</p>}</section>}
    <section className="chat-list">{detail?.messages.map((item) => <article className={item.senderType === 'PATIENT' ? 'mine-message' : 'doctor-message'} key={item.id}><small>{item.senderType === 'DOCTOR' ? '医生回复' : '患者消息'} · {item.createdAt}</small><p>{item.content}</p></article>)}</section>
    {detail?.status === 'PENDING' && <p className="empty-state">医生接诊后会在这里发送回复，患者无需再次输入消息。</p>}
    {detail?.prescriptionIds.length ? <section><h3>已批准处方</h3>{detail.prescriptionIds.map((id) => <button className="record-card" type="button" key={id} onClick={() => navigate(buildAssistantPrescriptionDetailPath(id, patientId, detail.preConsultation?.submittedAt))}><span>处方 #{id}</span><ChevronRight size={18} /></button>)}</section> : null}
  </section>{notice && <div className="toast" onClick={() => setNotice('')}>{notice}</div>}</main>;
}
