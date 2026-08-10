import { useEffect, useState } from 'react';
import { ArrowLeft, ChevronRight, House, RefreshCw } from 'lucide-react';
import { useLocation, useNavigate } from 'umi';
import { AgentChat } from '../../components/agent/AgentChat';
import { clearAgentSessionId, resolveAgentContext } from '../../models/agent';
import { getSelection, resolveSelectedPatientId, saveSelection } from '../../models/selection';
import { Dialog } from '../../components/Dialog';
import { getFamilyMembers } from '../../services/family';
import type { AgentChatContext, AgentNavigationState, AgentPresetAction } from '../../typings/agent';
import type { FamilyMember } from '../../typings/api';

/** AI 助手全屏会话页。 */
export default function AgentPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const routeState = location.state as AgentNavigationState | null;
  const fromPath = routeState?.from || location.pathname;
  const presetAction = resolvePresetAction(routeState?.presetAction);
  const resumeSessionId = resolveResumeSessionId(routeState?.resumeSessionId);
  // 异步加载完整对话上下文（默认收货地址 + 基础字段）
  const [context, setContext] = useState<AgentChatContext | undefined>(undefined);
  const [members, setMembers] = useState<FamilyMember[]>([]);
  const [patientId, setPatientId] = useState<number | undefined>(() => getSelection().patientId);
  const [patientReady, setPatientReady] = useState(false);
  const [patientOpen, setPatientOpen] = useState(false);
  const [hasSwitchedPatient, setHasSwitchedPatient] = useState(false);

  /** 初始化全局就诊人；直接进入 AI 助手时也必须先确定本人或家属上下文。 */
  useEffect(() => {
    let cancelled = false;
    getFamilyMembers().then((nextMembers) => {
      if (cancelled) return;
      const nextPatientId = resolveSelectedPatientId(nextMembers, getSelection().patientId);
      if (nextPatientId) saveSelection({ patientId: nextPatientId });
      setMembers(nextMembers);
      setPatientId(nextPatientId);
      setPatientReady(true);
    }).catch(() => {
      // 成员请求失败时保留已有全局选择，避免阻塞可继续使用的 AI 会话。
      if (!cancelled) setPatientReady(true);
    });
    return () => { cancelled = true; };
  }, []);

  useEffect(() => {
    if (!patientReady) {
      setContext(undefined);
      return undefined;
    }
    let cancelled = false;
    resolveAgentContext(fromPath).then((next) => {
      if (!cancelled) setContext(next);
    });
    return () => { cancelled = true; };
  }, [fromPath, patientId, patientReady]);

  /**
   * 切换 AI 本次会话使用的全局就诊人。
   * @param nextPatientId 新选择的本人或家属 ID
   * @returns 无返回值
   */
  function selectPatient(nextPatientId: number) {
    if (nextPatientId === patientId) {
      setPatientOpen(false);
      return;
    }
    // 切换患者不能继续复用旧会话，避免后续请求误带上一位就诊人的上下文。
    clearAgentSessionId();
    saveSelection({ patientId: nextPatientId });
    setContext(undefined);
    setPatientId(nextPatientId);
    setHasSwitchedPatient(true);
    setPatientOpen(false);
  }

  const currentPatient = members.find((member) => member.patientId === patientId);
  const activeResumeSessionId = hasSwitchedPatient ? undefined : resumeSessionId;

  return (
    <main className="agent-page">
      <header className="agent-page__header">
        <div className="agent-page__controls"><button type="button" className="agent-page__back" onClick={() => navigate(-1)} aria-label="返回"><ArrowLeft size={22} /></button><button type="button" className="agent-page__home" onClick={() => navigate('/home')} aria-label="返回首页"><House size={19} /></button></div>
        <h1>AI 助手</h1>
        <span aria-hidden="true" />
      </header>
      <button className="agent-page__patient-switch" type="button" onClick={() => setPatientOpen(true)}>
        <span>当前就诊人</span><b>{currentPatient?.name || '加载中...'}</b><em>切换 <RefreshCw size={14} /></em><ChevronRight size={16} aria-hidden="true" />
      </button>
      <AgentChat
        key={`agent-${patientId || 'unresolved'}-${hasSwitchedPatient ? 'switched' : 'initial'}`}
        context={context}
        presetAction={presetAction}
        resumeSessionId={activeResumeSessionId}
        returnPath={fromPath}
      />
      {patientOpen && <Dialog title="切换就诊人" onClose={() => setPatientOpen(false)}>{members.map((member) => <button className="choice-row" key={member.patientId} type="button" onClick={() => selectPatient(member.patientId)}><span>{member.name}</span><small>{member.relationName || member.relation}{member.patientId === patientId ? ' · 当前选择' : ''}</small></button>)}{!members.length && <p className="empty-state">暂无可切换的就诊人</p>}</Dialog>}
    </main>
  );
}

/**
 * 校验路由状态中的一次性预设动作。
 * @param action 路由状态传入的候选动作
 * @returns 合法预设动作；非法数据不触发自动调用
 */
function resolvePresetAction(action: AgentPresetAction | undefined): AgentPresetAction | undefined {
  if (action?.type === 'interpret_prescription' && Number.isInteger(action.prescriptionId) && action.prescriptionId > 0) {
    return action;
  }
  if (action?.type === 'interpret_medical_record' && Number.isInteger(action.consultId) && action.consultId > 0) {
    return action;
  }
  if (action?.type === 'notify_drug_order_paid' && Number.isInteger(action.drugOrderId) && action.drugOrderId > 0) {
    return action;
  }
  if (action?.type === 'notify_appointment_paid' && Number.isInteger(action.appointmentId) && action.appointmentId > 0) {
    return action;
  }
  if (action?.type === 'quick_message' && typeof action.content === 'string' && action.content.trim()) {
    return action;
  }
  return undefined;
}

/**
 * 校验支付后恢复的原会话 ID。
 * @param sessionId 路由状态传入的候选会话 ID。
 * @returns 可恢复会话 ID；非法值返回 undefined。
 */
function resolveResumeSessionId(sessionId: string | undefined): string | undefined {
  if (!sessionId || !sessionId.trim()) {
    return undefined;
  }
  return sessionId;
}
