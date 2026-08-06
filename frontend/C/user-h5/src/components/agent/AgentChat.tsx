import { useCallback, useEffect, useRef, useState } from 'react';
import { ArrowLeft, Clock, Loader, MessageSquare, Plus, Send, Trash2 } from 'lucide-react';
import { useAgentStream } from '../../hooks/useAgentStream';
import { getSessions, deleteSession } from '../../services/agent';
import { AGENT_CONTENT_MAX, AGENT_QUICK_PROMPTS, AGENT_WELCOME } from '../../constants/agent';
import { AgentMessageBubble } from './AgentMessage';
import { AgentThoughtPanel } from './AgentThought';
import { AgentToolCardView } from './AgentToolCard';
import { AgentConfirmCardView } from './AgentConfirmCard';
import { AgentSelectCardView } from './AgentSelectCard';
import type { AgentChatContext, AgentConfirmCard, AgentPresetAction, AgentSession } from '../../typings/agent';

/**
 * 格式化会话时间：今天显示时分，昨天显示"昨天"，更早显示日期。
 */
function formatSessionTime(isoString: string): string {
  const date = new Date(isoString);
  const now = new Date();
  const isToday = date.toDateString() === now.toDateString();
  const yesterday = new Date(now);
  yesterday.setDate(yesterday.getDate() - 1);
  const isYesterday = date.toDateString() === yesterday.toDateString();

  if (isToday) {
    return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
  }
  if (isYesterday) {
    return '昨天';
  }
  return date.toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' });
}

/**
 * AI 助手会话 UI：消息流 + 输入框 + 连接状态 + L2 确认卡片 + 历史会话列表。
 *
 * 由 /agent 全屏页承载。对外部传入的上下文（当前页面、医院、就诊人）透传给 Agent。
 */
export function AgentChat({ context, presetAction }: { context?: AgentChatContext; presetAction?: AgentPresetAction }) {
  const {
    entries,
    connection,
    errorMessage,
    isStreaming,
    send,
    confirm,
    selectOption,
    cancel,
    retry,
    reset,
    loadSession,
  } = useAgentStream();
  const [input, setInput] = useState('');
  const listRef = useRef<HTMLDivElement>(null);
  const executedPresetRef = useRef<string>();

  useEffect(() => {
    if (!context || !presetAction) return;
    const presetKey = `${presetAction.type}:${presetAction.prescriptionId}`;
    // 严格模式重挂载与上下文异步就绪时只允许自动发送一次。
    if (executedPresetRef.current === presetKey) return;
    executedPresetRef.current = presetKey;
    // 仅传业务 ID，由 Agent 固定调用受控处方解读工具，避免暴露处方正文。
    send('请为我解读当前处方。', {
      ...context,
      preset_action: presetAction.type,
      prescription_id: presetAction.prescriptionId,
    }, { startNewSession: true });
  }, [context, presetAction, send]);

  // 历史会话相关状态
  const [showHistory, setShowHistory] = useState(false);
  const [sessions, setSessions] = useState<AgentSession[]>([]);
  const [sessionsLoading, setSessionsLoading] = useState(false);
  const [sessionsError, setSessionsError] = useState('');

  // 新消息到达或流式累加时，自动滚动到底部
  useEffect(() => {
    if (listRef.current) listRef.current.scrollTop = listRef.current.scrollHeight;
  }, [entries]);

  /** 加载历史会话列表 */
  const loadSessions = useCallback(async () => {
    setSessionsLoading(true);
    setSessionsError('');
    try {
      const list = await getSessions();
      setSessions(list);
    } catch (err) {
      setSessionsError((err as Error).message || '加载失败');
    } finally {
      setSessionsLoading(false);
    }
  }, []);

  /** 切换显示历史会话列表 */
  const toggleHistory = useCallback(() => {
    if (!showHistory) {
      void loadSessions();
    }
    setShowHistory((prev) => !prev);
  }, [showHistory, loadSessions]);

  /** 选择一个历史会话 */
  const handleSelectSession = useCallback(
    (sessionId: string) => {
      setShowHistory(false);
      void loadSession(sessionId);
    },
    [loadSession],
  );

  /** 删除一个历史会话 */
  const handleDeleteSession = useCallback(
    async (sessionId: string, e: React.MouseEvent) => {
      e.stopPropagation();
      if (!window.confirm('确定要删除这个会话吗？')) return;

      try {
        await deleteSession(sessionId);
        // 从列表中移除
        setSessions((prev) => prev.filter((s) => s.session_id !== sessionId));
      } catch (err) {
        setSessionsError((err as Error).message || '删除失败');
      }
    },
    [],
  );

  /** 新建会话 */
  const handleNewSession = useCallback(() => {
    setShowHistory(false);
    reset();
  }, [reset]);

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
        <div className="agent-chat__actions">
          {entries.length > 0 && (
            <button type="button" className="agent-chat__action-btn" onClick={handleNewSession} aria-label="新会话">
              <Plus size={15} />
            </button>
          )}
          <button
            type="button"
            className={`agent-chat__action-btn ${showHistory ? 'agent-chat__action-btn--active' : ''}`}
            onClick={toggleHistory}
            aria-label="历史会话"
          >
            <Clock size={15} />
          </button>
        </div>
      </div>

      {/* 历史会话列表 */}
      {showHistory && (
        <div className="agent-chat__history">
          <div className="agent-chat__history-header">
            <h3>历史会话</h3>
            <button type="button" className="agent-chat__history-close" onClick={() => setShowHistory(false)}>
              <ArrowLeft size={18} />
            </button>
          </div>
          <div className="agent-chat__history-list">
            {sessionsLoading && (
              <div className="agent-chat__history-loading">
                <Loader size={16} className="agent-spin" />
                <span>加载中...</span>
              </div>
            )}
            {sessionsError && (
              <div className="agent-chat__history-error">
                <span>{sessionsError}</span>
                <button type="button" onClick={() => void loadSessions()}>
                  重试
                </button>
              </div>
            )}
            {!sessionsLoading && !sessionsError && sessions.length === 0 && (
              <div className="agent-chat__history-empty">
                <MessageSquare size={24} />
                <span>暂无历史会话</span>
              </div>
            )}
            {sessions.map((session) => (
              <div key={session.session_id} className="agent-chat__history-item">
                <button
                  type="button"
                  className="agent-chat__history-item-main"
                  onClick={() => handleSelectSession(session.session_id)}
                >
                  <div className="agent-chat__history-item-title">{session.title || '新会话'}</div>
                  <div className="agent-chat__history-item-preview">
                    {session.last_message || '暂无消息'}
                  </div>
                  <div className="agent-chat__history-item-meta">
                    <span className="agent-chat__history-item-count">{session.message_count}轮对话</span>
                    <span className="agent-chat__history-item-time">{formatSessionTime(session.updated_at)}</span>
                  </div>
                </button>
                <button
                  type="button"
                  className="agent-chat__history-item-delete"
                  onClick={(e) => void handleDeleteSession(session.session_id, e)}
                  aria-label="删除会话"
                >
                  <Trash2 size={16} />
                </button>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* 主聊天区域 */}
      {!showHistory && (
        <>
          {errorMessage && (
            <div className="agent-chat__error">
              <span>{errorMessage}</span>
              <button type="button" className="agent-chat__retry" onClick={retry}>
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
              if (entry.kind === 'select')
                return (
                  <AgentSelectCardView
                    key={entry.data.id}
                    card={entry.data}
                    disabled={isStreaming}
                    onSelect={(item) => selectOption(entry.data, item)}
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
        </>
      )}
    </div>
  );
}
