import { useState } from 'react';
import { DownOutlined, LoadingOutlined, ToolOutlined } from '@ant-design/icons';
import type { AgentToolCard } from '../../typings/agent';

/** 工具调用卡片：action 与 observation 配对，展示 loading / 成功 / 失败。 */
export function AgentToolCardView({ card }: { card: AgentToolCard }) {
  const [expanded, setExpanded] = useState(false);
  const hasArgs = card.arguments && Object.keys(card.arguments).length > 0;
  const hasResult = card.result !== undefined && card.result !== null;
  return (
    <div className={`agent-tool agent-tool--${card.status}`}>
      <div className="agent-tool__header">
        <span className="agent-tool__icon">
          {card.status === 'loading' ? (
            <LoadingOutlined style={{ color: '#1890ff' }} />
          ) : (
            <ToolOutlined style={{ color: '#8c8c8c' }} />
          )}
        </span>
        <span className="agent-tool__label">{card.label}</span>
        <span className="agent-tool__status">
          {card.status === 'loading' && '调用中'}
          {card.status === 'success' && '成功'}
          {card.status === 'error' && '失败'}
        </span>
        {(hasArgs || hasResult) && (
          <button
            type="button"
            className="agent-tool__toggle"
            onClick={() => setExpanded((v) => !v)}
            aria-label="展开详情"
          >
            <DownOutlined className={expanded ? 'agent-tool__chevron is-open' : 'agent-tool__chevron'} />
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
