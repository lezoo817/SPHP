import { useEffect, useState } from 'react';
import { ChevronRight } from 'lucide-react';
import { useLocation, useNavigate, useParams } from 'umi';
import { PageHeader } from '../../components/PageHeader';
import { getSelection } from '../../models/selection';
import { getConsultation, sendConsultationMessage } from '../../services/consultation';
import { createConsultationSocket } from '../../services/consultation-socket';
import { getFamilyMembers } from '../../services/family';
import type { ConsultationDetail } from '../../typings/api';
import { createIdempotencyKey, getApiErrorMessage } from '../../utils/form';
import { formatConsultationMessageTime } from '../../utils/consultation';
import { buildAssistantPrescriptionDetailPath } from '../../utils/prescription';
import { resolveConsultationAgentReturnState } from '../../utils/agent-consultation';

/** 展示在线问诊的预问诊、实时文字消息和已批准处方。 */
export default function ConsultationPage() {
  const { consultationId } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const [detail, setDetail] = useState<ConsultationDetail>();
  const [patientName, setPatientName] = useState('当前就诊人');
  const [notice, setNotice] = useState('');
  const [content, setContent] = useState('');
  const [sending, setSending] = useState(false);
  const patientIdFromUrl = Number(new URLSearchParams(location.search).get('patientId')) || undefined;
  const returnToAgent = resolveConsultationAgentReturnState((location.state as { returnToAgent?: unknown } | null)?.returnToAgent);

  /** 读取问诊详情，通知跳转后可直接看到医生最新回复。 */
  async function load() {
    try {
      const nextDetail = await getConsultation(Number(consultationId));
      setDetail(nextDetail);
      // 使用当前账号可见成员列表将问诊患者 ID 映射为姓名。
      const members = await getFamilyMembers();
      const currentPatientId = nextDetail.patientId || patientIdFromUrl || getSelection().patientId;
      setPatientName(members.find((member) => member.patientId === currentPatientId)?.name || '当前就诊人');
    } catch (error) {
      setNotice(getApiErrorMessage(error));
    }
  }

  useEffect(() => { void load(); }, [consultationId]);

  useEffect(() => {
    const socket = createConsultationSocket((message) => {
      if (message.consultationId !== Number(consultationId)) return;
      setDetail((current) => {
        if (!current || current.messages.some((item) => item.id === message.messageId)) return current;
        return { ...current, messages: [...current.messages, { id: message.messageId, senderType: message.senderType, content: message.content, createdAt: message.createdAt }] };
      });
    });
    return () => { void socket?.deactivate(); };
  }, [consultationId]);

  /** 发送患者文字消息；使用同一 UUID 作为接口幂等键和客户端消息标识。 */
  async function sendMessage() {
    if (!content.trim() || sending || detail?.status !== 'IN_PROGRESS') return;
    setSending(true);
    try {
      const result = await sendConsultationMessage(Number(consultationId), content.trim(), createIdempotencyKey());
      setDetail((current) => current && current.messages.some((item) => item.id === result.messageId) ? current : current ? {
        ...current,
        messages: [...current.messages, { id: result.messageId, senderType: 'PATIENT', content: result.content, createdAt: result.createdAt }],
      } : current);
      setContent('');
    } catch (error) {
      setNotice(getApiErrorMessage(error));
    } finally {
      setSending(false);
    }
  }

  const statusText = detail?.status === 'PENDING' ? '等待医生回复' : detail?.status === 'IN_PROGRESS' ? '医生接诊中' : detail?.status === 'COMPLETED' ? '问诊已完成' : '问诊已结束';
  const patientId = detail?.patientId || patientIdFromUrl || getSelection().patientId;
  /** 从 AI 入口进入时恢复原会话，其余入口仍返回问诊记录页。 */
  function returnFromConsultation() {
    if (!returnToAgent) {
      navigate('/assistant');
      return;
    }
    navigate('/agent', {
      replace: true,
      state: { from: returnToAgent.from, resumeSessionId: returnToAgent.sessionId },
    });
  }

  return <main className="subpage"><PageHeader title="在线问诊" onBack={returnFromConsultation} /><section className="subpage-content chat-page">
    <h2>{detail?.doctor.name || '在线问诊'}</h2>
    <p>{statusText}</p>
    {detail?.preConsultation && <section className="consultation-summary"><h3>就诊人：{patientName}</h3><div className="consultation-summary__table"><b>主诉</b><p>{detail.preConsultation.chiefComplaint}</p>{detail.preConsultation.historyOfPresentIllness && <><b>现病史</b><p>{detail.preConsultation.historyOfPresentIllness}</p></>}</div></section>}
    <section className="chat-list">{detail?.messages.map((item) => <article className={item.senderType === 'PATIENT' ? 'mine-message' : 'doctor-message'} key={item.id}><small>{item.senderType === 'DOCTOR' ? '医生回复' : '患者消息'} · {formatConsultationMessageTime(item.createdAt)}</small><p>{item.content}</p></article>)}</section>
    {detail?.status === 'PENDING' && <p className="empty-state">医生接诊后可在这里与医生实时沟通。</p>}
    {detail?.status === 'IN_PROGRESS' && <section className="chat-input"><textarea value={content} maxLength={2000} placeholder="输入要发送给医生的消息" onChange={(event) => setContent(event.target.value)} /><button type="button" disabled={!content.trim() || sending} onClick={() => void sendMessage()}>{sending ? '发送中' : '发送'}</button></section>}
    {detail?.prescriptionIds.length ? <section><h3>已批准处方</h3>{detail.prescriptionIds.map((id) => <button className="record-card" type="button" key={id} onClick={() => navigate(buildAssistantPrescriptionDetailPath(id, patientId, detail.preConsultation?.submittedAt, Number(consultationId)))}><span>处方 #{id}</span><ChevronRight size={18} /></button>)}</section> : null}
  </section>{notice && <div className="toast" onClick={() => setNotice('')}>{notice}</div>}</main>;
}
