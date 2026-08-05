import type { AgentMessage } from '../../typings/agent';
import { AGENT_DISCLAIMER } from '../../constants/agent';

/** 渲染单条对话消息（用户或 AI）。 */
export function AgentMessageBubble({ message }: { message: AgentMessage }) {
  const isUser = message.role === 'user';
  return (
    <div className={isUser ? 'agent-msg agent-msg--user' : 'agent-msg agent-msg--ai'}>
      {!isUser && <div className="agent-msg__avatar">AI</div>}
      <div className="agent-msg__bubble">
        <p className="agent-msg__text">{message.content || (message.streaming ? '…' : '')}</p>
        {!isUser && message.content && !message.streaming && (
          <p className="agent-msg__disclaimer">{AGENT_DISCLAIMER}</p>
        )}
      </div>
    </div>
  );
}
