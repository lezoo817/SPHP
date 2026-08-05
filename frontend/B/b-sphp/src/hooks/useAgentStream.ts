/**
 * useAgentStream —— B 端 AI 辅助面板流式对话核心 Hook。
 *
 * 职责：
 * 1. 维护会话条目列表（消息、思考、工具卡片、确认卡片），按到达顺序排列。
 * 2. 解析七类 SSE 事件并更新对应条目：
 *    - message.delta 追加到当前 AI 消息；
 *    - thought.delta 追加到当前思考片段；
 *    - action 创建 loading 工具卡片，observation 按 tool 配对更新；
 *    - card 创建待确认卡片，确认后更新为 done / error；
 *    - error 展示错误并保留输入，done 结束本轮并保存 session_id。
 * 3. 暴露 send / confirm / cancel / reset / loadSession，供页面调用。
 *
 * 与 sphp-agent `app/api/routes/chat.py` 的 SSE 事件契约对齐。
 * B 端会话策略：一次问诊一个会话（切换患者时清除）。
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import {
  chatStream,
  confirmCard,
  deleteSession,
  getSessions,
  getSessionMessages,
  type ChatStreamHandle,
} from '../services/agent';
import { AGENT_TOOL_LABELS, AGENT_ERROR_TEXT } from '../constants/agent';
import {
  clearAgentSessionId,
  getAgentSessionId,
  saveAgentSessionId,
} from '../models/agent';

/** 生成稳定的前端 ID。 */
function genId(prefix: string): string {
  return `${prefix}_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 8)}`;
}

/** 按 tool 英文名推导中文标签。 */
function labelOf(tool: string): string {
  return AGENT_TOOL_LABELS[tool] || tool || '工具调用';
}

/** Hook 返回值。 */
export interface UseAgentStream {
  /** 会话条目（消息、思考、工具卡片、确认卡片），按顺序渲染 */
  entries: Agent.Entry[];
  /** 连接状态 */
  connection: Agent.ConnectionState;
  /** 最近一次错误提示（用于面板顶部提示） */
  errorMessage: string;
  /** 当前会话 ID */
  sessionId: string | undefined;
  /** 是否正在流式接收 */
  isStreaming: boolean;
  /** 发送一条用户消息并开启流式对话 */
  send: (content: string, context?: Agent.ChatContext) => void;
  /** 确认一张 L2 卡片 */
  confirm: (card: Agent.ConfirmCard) => Promise<void>;
  /** 中断当前流式请求 */
  cancel: () => void;
  /** 清空会话并重置状态 */
  reset: () => void;
  /** 加载指定历史会话 */
  loadSession: (sessionId: string) => Promise<void>;
  /** 历史会话列表 */
  sessions: Agent.Session[];
  /** 历史会话加载状态 */
  sessionsLoading: boolean;
  /** 刷新历史会话列表 */
  refreshSessions: () => Promise<void>;
  /** 删除历史会话 */
  removeSession: (sessionId: string) => Promise<void>;
}

/**
 * 管理 B 端 AI 辅助面板流式对话状态。
 * @returns 会话状态与操作方法
 */
export function useAgentStream(): UseAgentStream {
  const [entries, setEntries] = useState<Agent.Entry[]>([]);
  const [connection, setConnection] = useState<Agent.ConnectionState>('idle');
  const [errorMessage, setErrorMessage] = useState('');
  const [sessionId, setSessionId] = useState<string | undefined>(getAgentSessionId());
  const [sessions, setSessions] = useState<Agent.Session[]>([]);
  const [sessionsLoading, setSessionsLoading] = useState(false);

  const handleRef = useRef<ChatStreamHandle | null>(null);
  // 当前 AI 消息 ID：同轮流式 message.delta 累加到同一条消息
  const currentMessageIdRef = useRef<string | null>(null);
  // 当前思考片段 ID：同轮 thought.delta 累加到同一条思考
  const currentThoughtIdRef = useRef<string | null>(null);

  /** 中断当前流式请求并释放读取器。 */
  const cancel = useCallback(() => {
    if (handleRef.current) {
      handleRef.current.abort();
      handleRef.current = null;
    }
  }, []);

  /** 清空会话并重置全部状态。 */
  const reset = useCallback(() => {
    cancel();
    clearAgentSessionId();
    setEntries([]);
    setSessionId(undefined);
    setErrorMessage('');
    setConnection('idle');
    currentMessageIdRef.current = null;
    currentThoughtIdRef.current = null;
  }, [cancel]);

  /** 更新指定确认卡片的部分字段。 */
  function updateConfirmCard(cardId: string, patch: Partial<Agent.ConfirmCard>): void {
    setEntries((prev) =>
      prev.map((entry) =>
        entry.kind === 'card' && entry.data.id === cardId
          ? { kind: 'card', data: { ...entry.data, ...patch } }
          : entry,
      ),
    );
  }

  /** 确认一张 L2 卡片：调用确认回调并按结果更新卡片状态。 */
  const confirm = useCallback(async (card: Agent.ConfirmCard) => {
    // 令牌过期校验：到期后禁用卡片，不再调用确认接口
    if (card.expiresAt && Date.parse(card.expiresAt) <= Date.now()) {
      updateConfirmCard(card.id, {
        status: 'expired',
        errorCode: 'CONFIRM_EXPIRED',
        errorMessage: '确认已超时，请重新发起操作',
      });
      return;
    }
    updateConfirmCard(card.id, { status: 'confirming' });
    try {
      const result = await confirmCard({
        confirm_token: card.confirmToken,
        session_id: card.sessionId,
      });
      updateConfirmCard(card.id, {
        status: 'done',
        resultMessage: result.message || '操作成功',
      });
    } catch (error) {
      const code = (error as Error & { code?: string }).code;
      const text =
        (code && AGENT_ERROR_TEXT[code]) ||
        (code === 'CONFIRM_EXPIRED' && '确认已超时，请重新发起操作') ||
        (code === 'CONFIRM_CONSUMED' && '已处理，无需重复确认') ||
        (code === 'CONFIRM_INVALID' && '确认参数无效，请重新发起操作') ||
        (code === 'SESSION_MISMATCH' && '会话不匹配，请重新发起操作') ||
        (code === 'TOOL_FAILED' && '操作执行失败，请稍后重试') ||
        (error as Error).message ||
        '确认失败，请稍后重试';
      updateConfirmCard(card.id, {
        status: 'error',
        errorCode: code,
        errorMessage: text,
      });
    }
  }, []);

  /** 发送一条用户消息并开启流式对话。 */
  const send = useCallback(
    (content: string, context?: Agent.ChatContext) => {
      const text = content.trim();
      if (!text) return;
      // 连接中禁止重复发送
      if (connection === 'connecting' || connection === 'streaming') return;

      setErrorMessage('');
      // 重置同轮 AI 消息 / 思考累加指针
      currentMessageIdRef.current = null;
      currentThoughtIdRef.current = null;

      // 追加用户消息
      const userMessage: Agent.Message = {
        id: genId('u'),
        role: 'user',
        content: text,
        createdAt: Date.now(),
      };
      setEntries((prev) => [...prev, { kind: 'message', data: userMessage }]);

      setConnection('connecting');
      const currentSessionId = sessionId;

      handleRef.current = chatStream(
        text,
        (event) => handleSseEvent(event),
        (message, code) => {
          setConnection('error');
          setErrorMessage(AGENT_ERROR_TEXT[code || ''] || message || '对话异常，请重试');
          // 会话不存在时清除本地 sessionId，下轮创建新会话
          if (code === 'SESSION_NOT_FOUND') {
            clearAgentSessionId();
            setSessionId(undefined);
          }
        },
        { sessionId: currentSessionId, context },
      );
    },
    [connection, sessionId],
  );

  /** 处理单条 SSE 事件，更新对应条目。 */
  function handleSseEvent(event: Agent.SseEvent): void {
    switch (event.event) {
      case 'message':
        setConnection('streaming');
        appendMessageDelta(event.data.delta);
        break;
      case 'thought':
        setConnection('streaming');
        appendThoughtDelta(event.data.delta);
        break;
      case 'action':
        setConnection('streaming');
        appendToolCard(event.data);
        break;
      case 'observation':
        setConnection('streaming');
        updateToolCard(event.data);
        break;
      case 'card':
        setConnection('streaming');
        appendConfirmCard(event.data);
        break;
      case 'error':
        setConnection('error');
        setErrorMessage(event.data.message || AGENT_ERROR_TEXT[event.data.code] || '对话异常');
        if (event.data.code === 'SESSION_NOT_FOUND') {
          clearAgentSessionId();
          setSessionId(undefined);
        }
        break;
      case 'done':
        // 保存会话 ID，关闭流式状态
        if (event.data.session_id) {
          saveAgentSessionId(event.data.session_id);
          setSessionId(event.data.session_id);
        }
        finalizeStreaming();
        setConnection('idle');
        if (handleRef.current) {
          handleRef.current = null;
        }
        break;
    }
  }

  /** 追加 message.delta 到当前 AI 消息，无则新建。 */
  function appendMessageDelta(delta: string): void {
    if (!delta) return;
    setEntries((prev) => {
      let messageId = currentMessageIdRef.current;
      if (!messageId) {
        messageId = genId('a');
        currentMessageIdRef.current = messageId;
        const aiMessage: Agent.Message = {
          id: messageId,
          role: 'assistant',
          content: delta,
          streaming: true,
          createdAt: Date.now(),
        };
        return [...prev, { kind: 'message', data: aiMessage }];
      }
      return prev.map((entry) =>
        entry.kind === 'message' && entry.data.id === messageId && entry.data.role === 'assistant'
          ? { kind: 'message', data: { ...entry.data, content: entry.data.content + delta } }
          : entry,
      );
    });
  }

  /** 追加 thought.delta 到当前思考片段，无则新建。 */
  function appendThoughtDelta(delta: string): void {
    if (!delta) return;
    setEntries((prev) => {
      let thoughtId = currentThoughtIdRef.current;
      if (!thoughtId) {
        thoughtId = genId('t');
        currentThoughtIdRef.current = thoughtId;
        const thought: Agent.Thought = {
          id: thoughtId,
          content: delta,
          streaming: true,
          createdAt: Date.now(),
        };
        return [...prev, { kind: 'thought', data: thought }];
      }
      return prev.map((entry) =>
        entry.kind === 'thought' && entry.data.id === thoughtId
          ? { kind: 'thought', data: { ...entry.data, content: entry.data.content + delta } }
          : entry,
      );
    });
  }

  /** 创建一张 loading 工具卡片。 */
  function appendToolCard(action: { tool: string; arguments?: Record<string, unknown> }): void {
    const toolCard: Agent.ToolCard = {
      id: genId('tool'),
      tool: action.tool,
      label: labelOf(action.tool),
      arguments: action.arguments,
      status: 'loading',
      createdAt: Date.now(),
    };
    setEntries((prev) => [...prev, { kind: 'tool', data: toolCard }]);
  }

  /** 按 tool 配对最近一张 loading 工具卡片并更新为成功或失败。 */
  function updateToolCard(observation: {
    tool: string;
    status: 'success' | 'error';
    result?: unknown;
    summary?: string;
    duration_ms?: number;
    error?: string;
  }): void {
    setEntries((prev) => {
      let matchedIndex = -1;
      for (let i = prev.length - 1; i >= 0; i -= 1) {
        const entry = prev[i];
        if (entry.kind === 'tool' && entry.data.tool === observation.tool && entry.data.status === 'loading') {
          matchedIndex = i;
          break;
        }
      }
      if (matchedIndex === -1) {
        // 未匹配到 action：直接创建一张已完成卡片，避免结果丢失
        const fallback: Agent.ToolCard = {
          id: genId('tool'),
          tool: observation.tool,
          label: labelOf(observation.tool),
          status: observation.status,
          summary: observation.summary,
          result: observation.result,
          error: observation.error,
          durationMs: observation.duration_ms,
          createdAt: Date.now(),
        };
        return [...prev, { kind: 'tool', data: fallback }];
      }
      const matched = prev[matchedIndex] as { kind: 'tool'; data: Agent.ToolCard };
      const updated: Agent.ToolCard = {
        ...matched.data,
        status: observation.status,
        summary: observation.summary,
        result: observation.result,
        error: observation.error,
        durationMs: observation.duration_ms,
      };
      const next = prev.slice();
      next[matchedIndex] = { kind: 'tool', data: updated };
      return next;
    });
  }

  /** 追加一张待确认卡片。 */
  function appendConfirmCard(card: Agent.CardEvent): void {
    const confirmCardEntry: Agent.ConfirmCard = {
      id: genId('card'),
      cardType: card.card_type,
      confirmToken: card.confirm_token,
      sessionId: card.session_id,
      title: card.title,
      summary: card.summary,
      details: card.details,
      expiresAt: card.expires_at,
      status: 'pending',
      createdAt: Date.now(),
    };
    setEntries((prev) => [...prev, { kind: 'card', data: confirmCardEntry }]);
  }

  /** 结束本轮流式：标记当前 AI 消息与思考为非流式。 */
  function finalizeStreaming(): void {
    const messageId = currentMessageIdRef.current;
    const thoughtId = currentThoughtIdRef.current;
    setEntries((prev) =>
      prev.map((entry) => {
        if (entry.kind === 'message' && messageId && entry.data.id === messageId) {
          return { kind: 'message', data: { ...entry.data, streaming: false } };
        }
        if (entry.kind === 'thought' && thoughtId && entry.data.id === thoughtId) {
          return { kind: 'thought', data: { ...entry.data, streaming: false } };
        }
        return entry;
      }),
    );
    currentMessageIdRef.current = null;
    currentThoughtIdRef.current = null;
  }

  // 组件卸载时中断未完成的流式请求，避免内存泄漏
  useEffect(() => () => cancel(), [cancel]);

  /** 加载指定历史会话：获取历史消息并显示。 */
  const loadSession = useCallback(
    async (targetSessionId: string) => {
      // 重置当前状态
      cancel();
      setEntries([]);
      setErrorMessage('');
      setConnection('connecting');
      currentMessageIdRef.current = null;
      currentThoughtIdRef.current = null;

      saveAgentSessionId(targetSessionId);
      setSessionId(targetSessionId);

      try {
        const messages = await getSessionMessages(targetSessionId);
        const historyEntries: Agent.Entry[] = messages.map((msg) => ({
          kind: 'message' as const,
          data: {
            id: genId(msg.role === 'user' ? 'u' : 'a'),
            role: msg.role as 'user' | 'assistant',
            content: msg.content,
            createdAt: Date.now(),
          },
        }));
        setEntries(historyEntries);
        setConnection('idle');
      } catch (err) {
        setConnection('error');
        setErrorMessage((err as Error).message || '加载历史消息失败');
      }
    },
    [cancel],
  );

  /** 刷新历史会话列表。 */
  const refreshSessions = useCallback(async () => {
    setSessionsLoading(true);
    try {
      const list = await getSessions();
      setSessions(list);
    } catch (err) {
      // 静默失败，仅记录日志，不打断对话
      // eslint-disable-next-line no-console
      console.warn('刷新历史会话失败：', (err as Error).message);
    } finally {
      setSessionsLoading(false);
    }
  }, []);

  /** 删除历史会话。 */
  const removeSession = useCallback(async (targetSessionId: string) => {
    await deleteSession(targetSessionId);
    setSessions((prev) => prev.filter((s) => s.session_id !== targetSessionId));
  }, []);

  return {
    entries,
    connection,
    errorMessage,
    sessionId,
    isStreaming: connection === 'connecting' || connection === 'streaming',
    send,
    confirm,
    cancel,
    reset,
    loadSession,
    sessions,
    sessionsLoading,
    refreshSessions,
    removeSession,
  };
}
