import { Check, FileText, Pill } from 'lucide-react';
import type { AgentRecordPickerCard } from '../../typings/agent';

/**
 * 解读记录选择卡：用户选中病历或处方后必须确认才会触发 AI 解读。
 */
export function AgentRecordPickerCardView({
  card,
  disabled,
  onSelect,
  onConfirm,
  onCancel,
}: {
  card: AgentRecordPickerCard;
  disabled?: boolean;
  onSelect: (recordId: number) => void;
  onConfirm: (card: AgentRecordPickerCard) => void;
  onCancel: (card: AgentRecordPickerCard) => void;
}) {
  const locked = disabled || card.status !== 'pending';
  const Icon = card.picker_type === 'prescription' ? Pill : FileText;
  return (
    <section className={`agent-record-picker agent-record-picker--${card.status}`}>
      <header className="agent-record-picker__header"><Icon size={18} /><h3>{card.title}</h3></header>
      <div className="agent-record-picker__list" role="radiogroup">
        {card.items.map((item) => {
          const selected = card.selectedId === item.id;
          return <button key={item.id} type="button" role="radio" aria-checked={selected} className={`agent-record-picker__item${selected ? ' is-selected' : ''}`} disabled={locked} onClick={() => onSelect(item.id)}><span><b>{item.title}</b><small>{item.description}</small></span>{selected && <Check size={17} />}</button>;
        })}
      </div>
      <footer className="agent-record-picker__actions">
        <button type="button" onClick={() => onCancel(card)} disabled={locked}>{card.cancel_text}</button>
        <button type="button" onClick={() => onConfirm(card)} disabled={locked || !card.selectedId}>{card.confirm_text}</button>
      </footer>
      {card.status === 'cancelled' && <p>已取消选择</p>}
      {card.status === 'confirmed' && <p>已确认，AI 正在解读...</p>}
    </section>
  );
}
