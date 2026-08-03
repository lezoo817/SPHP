import { useEffect, useRef, useState } from 'react';
import { Loader, RefreshCw, Send } from 'lucide-react';
import { useAgentStream } from '../../hooks/useAgentStream';
import { AGENT_CONTENT_MAX, AGENT_QUICK_PROMPTS, AGENT_WELCOME } from '../../constants/agent';
import { AgentMessageBubble } from './AgentMessage';
import { AgentThoughtPanel } from './AgentThought';
import { AgentToolCardView } from './AgentToolCard';
import { AgentConfirmCardView } from './AgentConfirmCard';
import type { AgentChatContext, AgentConfirmCard } from '../../typings/agent';

/**
 * AI 助手会话 UI：消息流 + 输入框 + 连接状态 + L2 确认卡片。
 *
 * 由 /agent 全屏页承载。对外部传入的上下文（当前页面、医院、就诊人）透传给 Agent。
 */
export function AgentChat({ context }: { context?: AgentChatContext }) {
  const {
    entries,
    connection,
    errorMessage,
    isStreaming,
    send,
    confirm,
    cancel,
    reset,
  } = useAgentStream();
  const [input, setInput] = useState('');
  const listRef = useRef<HTMLDivElement>(null);

  // 新消息到达或流式累加时，自动滚动到底部
  useEffect(() => {
    if (listRef.current) listRef.current.scrollTop = listRef.current.scrollHeight;
  }, [entries]);

  /** 提交当前输入。 */
  function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    const text = input.trim();
    if (!text || isStreaming) return;
    send(text, context);
    setInput('');
  }

  /** 点击快捷入口。 */
  function handleQuick(prompt: string) {
    if (isStreaming) return;
    send(prompt, context);
  }

  const showWelcome = entries.length === 0;

  return (
    <div className="agent-chat">
      <div className="agent-chat__bar">
        <span className="agent-chat__status">
          {isStreaming && <Loader size={14} className="agent-spin" />}
          {connection === 'connecting' && '连接中'}
          {connection === 'streaming' && '回复中'}
          {connection === 'error' && '连接异常'}
          {connection === 'idle' && 'AI 助手'}
        </span>
        {entries.length > 0 && (
          <button type="button" className="agent-chat__reset" onClick={reset} aria-label="新会话">
            <RefreshCw size={15} />
            <span>新会话</span>
          </button>
        )}
      </div>

      {errorMessage && (
        <div className="agent-chat__error">
          <span>{errorMessage}</span>
          <button type="button" className="agent-chat__retry" onClick={() => input && handleSubmit}>
            重试
          </button>
        </div>
      )}

      <div className="agent-chat__list" ref={listRef}>
        {showWelcome && (
          <div className="agent-chat__welcome">
            <p className="agent-chat__welcome-text">{AGENT_WELCOME}</p>
            <div className="agent-chat__quick">
              {AGENT_QUICK_PROMPTS.map((prompt) => (
                <button
                  type="button"
                  key={prompt.label}
                  className="agent-chat__quick-item"
                  onClick={() => handleQuick(prompt.content)}
                  disabled={isStreaming}
                >
                  {prompt.label}
                </button>
              ))}
            </div>
          </div>
        )}
        {entries.map((entry) => {
          if (entry.kind === 'message') return <AgentMessageBubble key={entry.data.id} message={entry.data} />;
          if (entry.kind === 'thought') return <AgentThoughtPanel key={entry.data.id} thought={entry.data} />;
          if (entry.kind === 'tool') return <AgentToolCardView key={entry.data.id} card={entry.data} />;
          if (entry.kind === 'card')
            return (
              <AgentConfirmCardView
                key={entry.data.id}
                card={entry.data}
                onConfirm={(card: AgentConfirmCard) => void confirm(card)}
              />
            );
          return null;
        })}
        {isStreaming && entries.length > 0 && (
          <button type="button" className="agent-chat__stop" onClick={cancel}>
            停止生成
          </button>
        )}
      </div>

      <form className="agent-chat__input-bar" onSubmit={handleSubmit}>
        <textarea
          className="agent-chat__input"
          placeholder="输入咨询内容，1 至 2000 字"
          value={input}
          onChange={(e) => setInput(e.target.value.slice(0, AGENT_CONTENT_MAX))}
          rows={1}
          disabled={isStreaming}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
              e.preventDefault();
              handleSubmit(e);
            }
          }}
        />
        <button type="submit" className="agent-chat__send" disabled={isStreaming || !input.trim()}>
          <Send size={18} />
        </button>
      </form>
    </div>
  );
}
