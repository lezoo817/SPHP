import { useState } from 'react';
import { Brain, ChevronDown } from 'lucide-react';
import type { AgentThought } from '../../typings/agent';

/** 推理过程面板的渲染参数。 */
interface AgentThoughtPanelProps {
  /** 当前流式或已完成的推理片段。 */
  thought: AgentThought;
}

/** 推理过程折叠面板：默认折叠，按需展开查看 thought 增量。 */
export function AgentThoughtPanel({ thought }: AgentThoughtPanelProps) {
  const [expanded, setExpanded] = useState(false);
  if (!thought.content) return null;
  return (
    <div className="agent-thought">
      <button
        type="button"
        className="agent-thought__header"
        onClick={() => setExpanded((v) => !v)}
        aria-expanded={expanded}
      >
        <Brain size={16} />
        <span>思考过程</span>
        <ChevronDown size={16} className={expanded ? 'agent-thought__chevron is-open' : 'agent-thought__chevron'} />
      </button>
      {expanded && <p className="agent-thought__content">{thought.content}</p>}
    </div>
  );
}
