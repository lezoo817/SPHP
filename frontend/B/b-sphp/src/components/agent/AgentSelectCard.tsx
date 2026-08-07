import { CheckOutlined, UnorderedListOutlined } from '@ant-design/icons';
import type { AgentSelectCard, AgentSelectItem } from '@/typings/agent';

/**
 * 可选项卡片：单选点选，区别于 L2 "确认一个操作" 卡片。
 *
 * 点选后其它选项置灰禁用，已选项高亮标记。
 * 流式接收时（disabled）所有按钮禁用，避免在 done 之前误触。
 */
export function AgentSelectCardView({
  card,
  disabled,
  onSelect,
}: {
  card: AgentSelectCard;
  disabled?: boolean;
  onSelect: (item: AgentSelectItem) => void;
}) {
  const locked = Boolean(card.selectedId) || Boolean(disabled);
  return (
    <div className={`agent-select${card.selectedId ? ' agent-select--selected' : ''}`}>
      <div className="agent-select__header">
        <span className="agent-select__icon">
          <UnorderedListOutlined />
        </span>
        <span className="agent-select__title">{card.prompt || '请选择一项'}</span>
      </div>
      <ul className="agent-select__list">
        {card.items.map((item) => {
          const isSelected = card.selectedId === item.id;
          return (
            <li key={item.id}>
              <button
                type="button"
                className={`agent-select__item${isSelected ? ' is-selected' : ''}`}
                disabled={locked && !isSelected}
                onClick={() => onSelect(item)}
              >
                <span className="agent-select__item-main">
                  <span className="agent-select__item-label">{item.label}</span>
                  {item.description && <span className="agent-select__item-desc">{item.description}</span>}
                </span>
                <span className="agent-select__item-mark" aria-hidden>
                  {isSelected && <CheckOutlined style={{ fontSize: 14 }} />}
                </span>
              </button>
            </li>
          );
        })}
      </ul>
      {card.selectedId && <p className="agent-select__hint">已选择，AI 正在为您继续处理...</p>}
    </div>
  );
}
