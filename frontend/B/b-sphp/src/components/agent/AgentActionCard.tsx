import { useState } from 'react';
import { EnvironmentOutlined } from '@ant-design/icons';
import type { AgentActionCard } from '@/typings/agent';

/**
 * 渲染不涉及 L2 写操作的受控业务交互卡（如推荐药店）。
 *
 * 视觉沿用 .agent-card 基调；点击按钮后立即锁定，防止同一推荐请求重复发送。
 *
 * @param props 卡片数据、禁用态与点击回调
 */
export function AgentActionCardView({
  card,
  disabled,
  onAction,
}: {
  card: AgentActionCard;
  disabled?: boolean;
  onAction: (card: AgentActionCard) => void;
}) {
  const [submitted, setSubmitted] = useState(false);
  const unavailable = Boolean(disabled) || submitted;

  /** 点击后立即锁定按钮，防止同一推荐请求重复发送。 */
  function handleAction() {
    if (unavailable) return;
    setSubmitted(true);
    onAction(card);
  }

  return (
    <div className="agent-card agent-action-card">
      <div className="agent-card__header">
        <span className="agent-card__icon">
          <EnvironmentOutlined />
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
