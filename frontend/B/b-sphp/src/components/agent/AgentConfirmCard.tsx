import { useState } from 'react';
import { CheckCircleOutlined, CloseCircleOutlined, SafetyOutlined } from '@ant-design/icons';
import type { AgentConfirmCard } from '../../typings/agent';
import { AGENT_CONFIRM_ERROR_TEXT } from '../../constants/agent';

/** 渲染 details 关键字段为可读键值对。 */
function renderDetails(details: Record<string, unknown> | undefined): { label: string; value: string }[] {
  if (!details) return [];
  const labelMap: Record<string, string> = {
    department_name: '科室',
    doctor_name: '医生',
    schedule_time: '就诊时段',
    fee_cent: '挂号费',
    appointment_id: '挂号订单',
    chief_complaint: '主诉',
    history_summary: '现病史',
    message_preview: '消息预览',
    pharmacy_name: '药房',
    drug_list: '药品',
    total_cent: '金额',
    drug_order_id: '购药订单',
    allergen: '过敏原',
    reaction: '过敏反应',
    action: '操作',
    content: '内容',
    occurred_at: '发生日期',
    report_name: '报告名称',
    report_date: '报告日期',
    indicators: '指标',
    plan_name: '用药计划',
    follow_up_type: '随访类型',
    remind_at: '提醒时间',
  };
  return Object.entries(details)
    .filter(([, v]) => v !== null && v !== undefined && v !== '')
    .map(([k, v]) => ({
      label: labelMap[k] || k,
      value: typeof v === 'object' ? JSON.stringify(v) : String(v),
    }));
}

/** 金额分转元展示。 */
function formatAmount(value: string, key: string): string {
  if ((key === 'fee_cent' || key === 'total_cent') && /^\d+$/.test(value)) {
    return `¥${(Number(value) / 100).toFixed(2)}`;
  }
  return value;
}

/**
 * L2 操作确认卡片。
 *
 * 状态机：pending -> confirming -> done / error / expired。
 * 令牌过期或已消费时置灰禁用，不再调用确认接口。
 */
export function AgentConfirmCardView({
  card,
  onConfirm,
}: {
  card: AgentConfirmCard;
  onConfirm: (card: AgentConfirmCard) => void;
}) {
  const [expanded, setExpanded] = useState(false);
  const detailRows = renderDetails(card.details);
  const expired = card.status === 'expired' || (!!card.expiresAt && Date.parse(card.expiresAt) <= Date.now());

  return (
    <div className={`agent-card agent-card--${card.status}`}>
      <div className="agent-card__header">
        <span className="agent-card__icon">
          {card.status === 'done' ? (
            <CheckCircleOutlined style={{ color: '#52c41a' }} />
          ) : card.status === 'error' || card.status === 'expired' ? (
            <CloseCircleOutlined style={{ color: '#ff4d4f' }} />
          ) : (
            <SafetyOutlined style={{ color: '#1890ff' }} />
          )}
        </span>
        <span className="agent-card__title">{card.title}</span>
      </div>
      <p className="agent-card__summary">{card.summary}</p>
      {detailRows.length > 0 && (
        <>
          <button
            type="button"
            className="agent-card__toggle"
            onClick={() => setExpanded((v) => !v)}
          >
            {expanded ? '收起详情' : '查看详情'}
          </button>
          {expanded && (
            <dl className="agent-card__details">
              {detailRows.map((row) => (
                <div className="agent-card__detail-row" key={row.label}>
                  <dt>{row.label}</dt>
                  <dd>{formatAmount(row.value, row.label)}</dd>
                </div>
              ))}
            </dl>
          )}
        </>
      )}
      {card.expiresAt && card.status === 'pending' && (
        <p className="agent-card__expire">令牌有效期至：{new Date(card.expiresAt).toLocaleString('zh-CN')}</p>
      )}
      {card.status === 'done' && card.resultMessage && (
        <p className="agent-card__result">{card.resultMessage}</p>
      )}
      {(card.status === 'error' || card.status === 'expired') && card.errorMessage && (
        <p className="agent-card__error-text">
          {card.errorMessage || AGENT_CONFIRM_ERROR_TEXT[card.errorCode || ''] || '操作失败'}
        </p>
      )}
      {card.status === 'pending' && (
        <div className="agent-card__actions">
          <button
            type="button"
            className="agent-card__confirm"
            disabled={expired}
            onClick={() => onConfirm(card)}
          >
            {expired ? '已过期' : '确认操作'}
          </button>
        </div>
      )}
      {card.status === 'confirming' && (
        <div className="agent-card__actions">
          <button type="button" className="agent-card__confirm" disabled>
            确认中…
          </button>
        </div>
      )}
    </div>
  );
}
