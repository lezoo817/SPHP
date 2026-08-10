import { useState } from 'react';
import { ChevronDown, Loader, Wrench } from 'lucide-react';
import type { AgentToolCard } from '../../typings/agent';

/** 工具调用卡片的渲染参数。 */
interface AgentToolCardViewProps {
  /** 已配对 action 与 observation 的工具调用状态。 */
  card: AgentToolCard;
}

/** 工具调用卡片：action 与 observation 配对，展示 loading / 成功 / 失败。 */
export function AgentToolCardView({ card }: AgentToolCardViewProps) {
  const [expanded, setExpanded] = useState(false);
  const hasArgs = card.arguments && Object.keys(card.arguments).length > 0;
  const hasResult = card.result !== undefined && card.result !== null;
  return (
    <div className={`agent-tool agent-tool--${card.status}`}>
      <div className="agent-tool__header">
        <span className="agent-tool__icon">
          {card.status === 'loading' ? <Loader size={15} className="agent-spin" /> : <Wrench size={15} />}
        </span>
        <span className="agent-tool__label">{card.label}</span>
        <span className="agent-tool__status">
          {card.status === 'loading' && '调用中'}
          {card.status === 'success' && '成功'}
          {card.status === 'pending' && '待确认'}
          {card.status === 'error' && '失败'}
        </span>
        {(hasArgs || hasResult) && (
          <button
            type="button"
            className="agent-tool__toggle"
            onClick={() => setExpanded((v) => !v)}
            aria-label="展开详情"
          >
            <ChevronDown size={15} className={expanded ? 'agent-tool__chevron is-open' : 'agent-tool__chevron'} />
          </button>
        )}
      </div>
      {card.summary && <p className="agent-tool__summary">{card.summary}</p>}
      {card.error && <p className="agent-tool__error">{card.error}</p>}
      {expanded && (
        <div className="agent-tool__detail">
          {hasArgs && (
            <div className="agent-tool__section">
              <p className="agent-tool__section-title">参数</p>
              <pre className="agent-tool__code">{JSON.stringify(card.arguments, null, 2)}</pre>
            </div>
          )}
          {hasResult && (
            <div className="agent-tool__section">
              <p className="agent-tool__section-title">结果</p>
              <pre className="agent-tool__code">{JSON.stringify(card.result, null, 2)}</pre>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
