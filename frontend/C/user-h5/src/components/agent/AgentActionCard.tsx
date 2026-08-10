import { Bell, MessageCircle, MapPinned } from 'lucide-react';
import { useState } from 'react';
import type { AgentActionCard } from '@/typings/agent';

/** 受控业务交互卡片的渲染参数。 */
interface AgentActionCardViewProps {
  /** 后端或前端受控生成的业务动作信息。 */
  card: AgentActionCard;
  /** 流式响应期间禁用提交。 */
  disabled?: boolean;
  /** 患者确认执行动作后的回调。 */
  onAction: (card: AgentActionCard) => void;
}

/**
 * 渲染不涉及写操作的受控业务交互卡。
 *
 * @param props 卡片数据和点击回调。
 * @returns 药店推荐等快捷业务动作卡片。
 */
export function AgentActionCardView({
  card,
  disabled,
  onAction,
}: AgentActionCardViewProps) {
  const [submitted, setSubmitted] = useState(false);
  const unavailable = disabled || submitted;

  /** 点击后立即锁定按钮，防止同一推荐请求重复发送。 */
  function handleAction() {
    if (unavailable) return;
    setSubmitted(true);
    onAction(card);
  }

  const isReminderAction = card.actionType === 'authorize_drug_order_reminder_after_receipt';
  const isConsultationRedirectAction = card.actionType === 'open_consultation_chat';

  return (
    <div className="agent-card agent-action-card">
      <div className="agent-card__header">
        <span className="agent-card__icon">
          {isReminderAction ? <Bell size={16} /> : isConsultationRedirectAction ? <MessageCircle size={16} /> : <MapPinned size={16} />}
        </span>
        <span className="agent-card__title">{card.title}</span>
      </div>
      <p className="agent-card__summary">{card.summary}</p>
      <div className="agent-card__actions">
        <button type="button" className="agent-card__confirm" disabled={unavailable} onClick={handleAction}>
          {submitted ? '正在处理...' : card.buttonText}
        </button>
      </div>
    </div>
  );
}
