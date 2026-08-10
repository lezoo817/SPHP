/**
 * useAgentStream —— AI 助手流式对话核心 Hook。
 *
 * 职责：
 * 1. 维护会话条目列表（消息、思考、工具卡片、确认卡片），按到达顺序排列。
 * 2. 解析八类 SSE 事件并更新对应条目：
 *    - message.delta 追加到当前 AI 消息；
 *    - thought.delta 追加到当前思考片段；
 *    - action 创建 loading 工具卡片，observation 按 tool 配对更新；
 *    - L2 工具卡不下发 observation（被挂起 pending_confirmations），收到对应 card 时收尾为 pending，确认成功翻为 success；
 *    - card 创建待确认卡片，确认后更新为 done / error；
 *    - options 创建可选项卡片，点选后发送"我选择{label}"；
 *    - error 展示错误并保留输入，done 结束本轮并保存 session_id。
 * 3. 暴露 send / confirm / selectOption / cancel / reset，供页面调用。
 *
 * 与 sphp-agent `app/api/routes/chat.py` 的 SSE 事件契约对齐。
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import { chatStream, confirmCard, getSessions, getSessionMessages, type ChatStreamHandle } from '../services/agent';
import { AGENT_TOOL_LABELS, AGENT_CARD_TYPE_TO_TOOL, AGENT_ERROR_TEXT } from '../constants/agent';
import {
  clearAgentSessionId,
  getAgentSessionId,
  saveAgentSessionId,
} from '../models/agent';
import type {
  AgentCardEvent,
  AgentActionCard,
  AgentActionCardEvent,
  AgentChatContext,
  AgentConfirmCard,
  AgentConfirmData,
  AgentConnectionState,
  AgentEntry,
  AgentHistoryCard,
  AgentMessage,
  AgentOptionsEvent,
  AgentRecordPickerCard,
  AgentRecordPickerEvent,
  AgentSelectCard,
  AgentSelectItem,
  AgentSseEvent,
  AgentThought,
  AgentToolCard,
} from '../typings/agent';

/** 生成稳定的前端 ID。 */
function genId(prefix: string): string {
  return `${prefix}_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 8)}`;
}

/** 按 tool 英文名推导中文标签。 */
function labelOf(tool: string): string {
  return AGENT_TOOL_LABELS[tool] || tool || '工具调用';
}

/** 将后端持久化的历史卡片还原为可渲染的会话条目（2026-08-10 卡片持久化）。
 *
 * payload 结构与 SSE 实时事件一致，还原逻辑与 handleSseEvent 的 append* 函数
 * 对齐；但不触发 resolveL2ToolCard（历史恢复无 loading 工具卡，避免空转），
 * 并按 expires_at 把已过期的 L2 确认卡置为 expired 只读态。
 *
 * @param cards 历史接口返回的持久化卡片列表（后端按 seq 升序）
 * @returns 可直接渲染的会话条目数组
 */
function buildHistoryCardEntries(cards: AgentHistoryCard[]): AgentEntry[] {
  const entries: AgentEntry[] = [];
  for (const card of cards) {
    if (card.event === 'card') {
      const p = card.payload;
      const expired = !!p.expires_at && Date.parse(p.expires_at) <= Date.now();
      entries.push({
        kind: 'card',
        data: {
          id: genId('card'),
          cardType: p.card_type,
          confirmToken: p.confirm_token,
          sessionId: p.session_id,
          title: p.title,
          summary: p.summary,
          details: p.details,
          expiresAt: p.expires_at,
          status: expired ? 'expired' : 'pending',
          ...(expired
            ? { errorCode: 'CONFIRM_EXPIRED', errorMessage: '确认已超时，请重新发起操作' }
            : {}),
          createdAt: Date.now(),
        },
      });
    } else if (card.event === 'action_card') {
      const p = card.payload;
      entries.push({
        kind: 'action',
        data: {
          id: genId('action'),
          actionType: p.action_type,
          title: p.title,
          summary: p.summary,
          buttonText: p.button_text,
          arguments: p.arguments || {},
          createdAt: Date.now(),
        },
      });
    } else if (card.event === 'record_picker') {
      entries.push({
        kind: 'record_picker',
        data: {
          ...card.payload,
          id: genId('record-picker'),
          status: 'pending' as const,
          createdAt: Date.now(),
        },
      });
    } else if (card.event === 'options') {
      const p = card.payload;
      entries.push({
        kind: 'select',
        data: {
          id: genId('sel'),
          selectType: p.type,
          items: p.items,
          prompt: p.prompt,
          replyTemplate: p.reply_template || '我选择{label}',
          createdAt: Date.now(),
        },
      });
    }
  }
  return entries;
}

/** Hook 返回值。 */
export interface UseAgentStream {
  /** 会话条目（消息、思考、工具卡片、确认卡片），按顺序渲染 */
  entries: AgentEntry[];
  /** 连接状态 */
  connection: AgentConnectionState;
  /** 最近一次错误提示（用于页面顶部提示） */
  errorMessage: string;
  /** 当前会话 ID */
  sessionId: string | undefined;
  /** 是否正在流式接收 */
  isStreaming: boolean;
  /** 发送一条用户消息并开启流式对话 */
  send: (content: string, context?: AgentChatContext, options?: AgentSendOptions) => void;
  /** 确认一张 L2 卡片（已支付取消挂号需传入登录密码） */
  confirm: (card: AgentConfirmCard, password?: string) => Promise<AgentConfirmData | undefined>;
  /** 用户从可选项卡片中点选一项：标记已选并发送"我选择{label}"消息 */
  selectOption: (card: AgentSelectCard, item: AgentSelectItem) => void;
  /** 选中解读记录，等待用户点击确认。 */
  selectRecordPicker: (cardId: string, recordId: number) => void;
  /** 更新记录选择卡确认或取消状态。 */
  updateRecordPicker: (cardId: string, status: AgentRecordPickerCard['status']) => void;
  /** 中断当前流式请求 */
  cancel: () => void;
  /** 重试上一条消息 */
  retry: () => void;
  /** 清空会话并重置状态 */
  reset: () => void;
  /** 加载指定历史会话 */
  loadSession: (sessionId: string) => Promise<void>;
}

/** 发送配置。 */
export interface AgentSendOptions {
  /** 强制创建独立会话，不继承浏览器中保存的会话 ID。 */
  startNewSession?: boolean;
  /** 受控快捷入口的内部触发文案不显示为用户消息。 */
  hideUserMessage?: boolean;
}

/**
 * 管理 AI 助手流式对话状态。
 * @returns 会话状态与操作方法
 */
export function useAgentStream(): UseAgentStream {
  const [entries, setEntries] = useState<AgentEntry[]>([]);
  const [connection, setConnection] = useState<AgentConnectionState>('idle');
  const [errorMessage, setErrorMessage] = useState('');
  const [sessionId, setSessionId] = useState<string | undefined>(getAgentSessionId());

  const handleRef = useRef<ChatStreamHandle | null>(null);
  // 当前 AI 消息 ID：同轮流式 message.delta 累加到同一条消息
  const currentMessageIdRef = useRef<string | null>(null);
  // 当前思考片段 ID：同轮 thought.delta 累加到同一条思考
  const currentThoughtIdRef = useRef<string | null>(null);
  // 保存最后一条用户消息，用于重试
  const lastUserMessageRef = useRef<string>('');
  const lastContextRef = useRef<AgentChatContext | undefined>(undefined);

  /** 中断当前流式请求并释放读取器。 */
  const cancel = useCallback(() => {
    if (handleRef.current) {
      handleRef.current.abort();
      handleRef.current = null;
    }
    // 标记当前 AI 消息与思考为非流式状态
    finalizeStreaming();
    // 重置连接状态为 idle，允许用户继续发送消息
    setConnection('idle');
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

  /** 确认一张 L2 卡片：调用确认回调并按结果更新卡片状态。 */
  const confirm = useCallback(
    async (card: AgentConfirmCard, password?: string): Promise<AgentConfirmData | undefined> => {
      // 令牌过期校验：到期后禁用卡片，不再调用确认接口
      if (card.expiresAt && Date.parse(card.expiresAt) <= Date.now()) {
        updateConfirmCard(card.id, {
          status: 'expired',
          errorCode: 'CONFIRM_EXPIRED',
          errorMessage: '确认已超时，请重新发起操作',
        });
        return undefined;
      }
      updateConfirmCard(card.id, { status: 'confirming' });
      try {
        const result = await confirmCard({
          confirm_token: card.confirmToken,
          session_id: card.sessionId,
          ...(password ? { login_password: password } : {}),
        });
        updateConfirmCard(card.id, {
          status: 'done',
          resultMessage: result.message || '操作成功',
        });
        // 确认成功后的业务结果文案（后端按工具返回友好文案，兜底"操作成功"）
        const successMessage = result.message || '操作成功';
        // 确认成功后，把对应 L2 工具卡从"待确认"收尾为成功（后端已在 confirm 时真正执行该工具）。
        // 否则该工具卡会永久停在"待确认"/"调用中"，用户点完提交仍看到上面在转圈。
        resolveL2ToolCard(card.cardType, { status: 'success', summary: successMessage });
        // 确认成功后追加一条 AI 消息到对话流，让用户更醒目地看到结果。
        // 不走 send()：send 会触发新的 chatStream 请求且 streaming 时会被拦截，
        // 这里直接 setEntries 追加纯文本消息（不发起请求、不消耗会话）。
        setEntries((prev) => [
          ...prev,
          {
            kind: 'message',
            data: {
              id: genId('a'),
              role: 'assistant',
              content: successMessage,
              createdAt: Date.now(),
            },
          },
        ]);
        return result;
      } catch (error) {
        const code = (error as Error & { code?: string }).code;
        // 鉴权失败：清理登录态由服务层完成，这里仅更新卡片
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
        return undefined;
      }
    },
    [], // updateConfirmCard 通过 setEntries 闭包稳定引用
  );

  /** 更新指定确认卡片的部分字段。 */
  function updateConfirmCard(
    cardId: string,
    patch: Partial<AgentConfirmCard>,
  ): void {
    setEntries((prev) =>
      prev.map((entry) =>
        entry.kind === 'card' && entry.data.id === cardId
          ? { kind: 'card', data: { ...entry.data, ...patch } }
          : entry,
      ),
    );
  }

  /** 发送一条用户消息并开启流式对话。 */
  const send = useCallback(
    (content: string, context?: AgentChatContext, options: AgentSendOptions = {}) => {
      const text = content.trim();
      if (!text) return;
      // 连接中禁止重复发送
      if (connection === 'connecting' || connection === 'streaming') return;

      setErrorMessage('');
      // 重置同轮 AI 消息 / 思考累加指针
      currentMessageIdRef.current = null;
      currentThoughtIdRef.current = null;

      // 一键入口必须切断旧会话，避免处方解读混入此前导诊或购药对话。
      if (options.startNewSession) {
        clearAgentSessionId();
        setSessionId(undefined);
        setEntries([]);
      }

      // 保存最后一条用户消息，用于重试
      lastUserMessageRef.current = text;
      lastContextRef.current = context;

      // 受控快捷入口仅用于触发固定业务流程，不向对话区伪造用户输入。
      if (!options.hideUserMessage) {
        const userMessage: AgentMessage = {
          id: genId('u'),
          role: 'user',
          content: text,
          createdAt: Date.now(),
        };
        setEntries((prev) => [...prev, { kind: 'message', data: userMessage }]);
      }

      setConnection('connecting');
      const currentSessionId = options.startNewSession ? undefined : sessionId;

      handleRef.current = chatStream(
        text,
        (event) => handleSseEvent(event, currentSessionId),
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

  /** 重试上一条消息。 */
  const retry = useCallback(() => {
    const text = lastUserMessageRef.current;
    if (!text) return;
    // 移除上一条用户消息（避免重复显示）
    setEntries((prev) => {
      const lastIndex = prev.length - 1;
      if (lastIndex >= 0 && prev[lastIndex].kind === 'message' && prev[lastIndex].data.role === 'user') {
        return prev.slice(0, -1);
      }
      return prev;
    });
    // 重新发送
    send(text, lastContextRef.current);
  }, [send]);

  /** 处理单条 SSE 事件，更新对应条目。 */
  function handleSseEvent(event: AgentSseEvent, currentSessionId?: string): void {
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
        case 'action_card':
          setConnection('streaming');
          appendActionCard(event.data);
          break;
      case 'options':
        setConnection('streaming');
        appendSelectCard(event.data);
        break;
      case 'record_picker':
        setConnection('streaming');
        appendRecordPicker(event.data);
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
    void currentSessionId;
  }

  /** 追加 message.delta 到当前 AI 消息，无则新建。 */
  function appendMessageDelta(delta: string): void {
    if (!delta) return;
    setEntries((prev) => {
      // 复用当前轮 AI 消息 ID
      let messageId = currentMessageIdRef.current;
      if (!messageId) {
        const lastEntry = prev[prev.length - 1];
        // SSE 末尾片段偶发晚于当前消息引用的清理时，继续拼接紧邻的助手消息，
        // 避免“请及时联系医生”这类同一句话被错误渲染成新的气泡。
        if (lastEntry?.kind === 'message' && lastEntry.data.role === 'assistant') {
          messageId = lastEntry.data.id;
          currentMessageIdRef.current = messageId;
          return prev.map((entry) =>
            entry.kind === 'message' && entry.data.id === messageId
              ? { kind: 'message', data: { ...entry.data, content: entry.data.content + delta, streaming: true } }
              : entry,
          );
        }
        messageId = genId('a');
        currentMessageIdRef.current = messageId;
        const aiMessage: AgentMessage = {
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
        const thought: AgentThought = {
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
    const toolCard: AgentToolCard = {
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
      // 从后往前找最近一张同 tool 且仍 loading 的卡片
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
        const fallback: AgentToolCard = {
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
      const matched = prev[matchedIndex] as { kind: 'tool'; data: AgentToolCard };
      const updated: AgentToolCard = {
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

  /**
   * 收尾一张 L2 工具卡：L2 工具（如 save_pre_consultation）在后端只下发 action，
   * 不会下发 observation（被挂起 pending_confirmations 等待确认），其 loading 卡片
   * 若不加处理会永久显示"调用中"。据此把它从 loading 收尾为 pending / success。
   * @param cardType 确认卡类型，反查对应的 L2 工具名
   * @param patch 要写入工具卡的状态（pending 待确认 / success 成功）
   */
  function resolveL2ToolCard(cardType: string, patch: Partial<AgentToolCard>): void {
    const tool = AGENT_CARD_TYPE_TO_TOOL[cardType];
    if (!tool) return;
    setEntries((prev) => {
      // 从后往前找最近一张同 tool 且仍 loading 的卡片（L2 工具卡不下发 observation，需在此收尾）
      let matchedIndex = -1;
      for (let i = prev.length - 1; i >= 0; i -= 1) {
        const entry = prev[i];
        if (
          entry.kind === 'tool' &&
          entry.data.tool === tool &&
          (entry.data.status === 'loading' || entry.data.status === 'pending')
        ) {
          matchedIndex = i;
          break;
        }
      }
      if (matchedIndex === -1) return prev;
      const matched = prev[matchedIndex] as { kind: 'tool'; data: AgentToolCard };
      const updated: AgentToolCard = { ...matched.data, ...patch };
      const next = prev.slice();
      next[matchedIndex] = { kind: 'tool', data: updated };
      return next;
    });
  }

  /** 追加一张待确认卡片。 */
  function appendConfirmCard(card: AgentCardEvent): void {
    const confirmCardEntry: AgentConfirmCard = {
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
    // L2 工具卡不下发 observation，收到对应确认卡时把 loading 收尾为"待确认"，
    // 避免永久显示"调用中"转圈（用户确认后再由 confirm 成功分支翻为 success）。
    resolveL2ToolCard(card.card_type, { status: 'pending', summary: '待您确认操作' });
  }


  /** 追加不涉及 L2 写操作的受控业务交互卡。 */
  function appendActionCard(card: AgentActionCardEvent): void {
    const actionCard: AgentActionCard = {
      id: genId('action'),
      actionType: card.action_type,
      title: card.title,
      summary: card.summary,
      buttonText: card.button_text,
      arguments: card.arguments || {},
      createdAt: Date.now(),
    };
    setEntries((prev) => [...prev, { kind: 'action', data: actionCard }]);
  }

  /** 追加病历或处方解读前的单选确认卡。 */
  function appendRecordPicker(card: AgentRecordPickerEvent): void {
    const recordPicker: AgentRecordPickerCard = {
      ...card,
      id: genId('record-picker'),
      status: 'pending',
      createdAt: Date.now(),
    };
    setEntries((prev) => [...prev, { kind: 'record_picker', data: recordPicker }]);
  }

  /** 由 options 事件构造一张可选项卡片（医生列表 / 科室列表 / 号源等）。 */
  function buildSelectCard(options: AgentOptionsEvent): AgentSelectCard {
    return {
      id: genId('sel'),
      selectType: options.type,
      items: options.items,
      prompt: options.prompt,
      replyTemplate: options.reply_template || '我选择{label}',
      createdAt: Date.now(),
    };
  }

  /**
   * 追加一张可选项卡片（医生列表 / 科室列表 / 号源等）。
   *
   * 去重策略（防后端跨轮重复推送选医生选项卡）：
   * - 如果已有同类型卡片且用户已点选（selectedId 非空）→ 忽略，不追加
   * - 如果已有同类型卡片但用户未点选 → 替换旧卡片（避免多张同类型卡堆叠）
   * - 无同类型卡片 → 正常追加
   */
  function appendSelectCard(options: AgentOptionsEvent): void {
    setEntries((prev) => {
      for (let i = prev.length - 1; i >= 0; i -= 1) {
        const entry = prev[i];
        if (entry.kind === 'select' && entry.data.selectType === options.type) {
          // 用户已点选过同类型卡片：忽略后续重复推送
          if (entry.data.selectedId) {
            return prev;
          }
          // 未点选：替换旧卡片（更新数据，避免同类型卡片堆积）
          const next = prev.slice();
          next[i] = { kind: 'select', data: buildSelectCard(options) };
          return next;
        }
      }
      return [...prev, { kind: 'select', data: buildSelectCard(options) }];
    });
  }

  /** 更新指定可选项卡片的部分字段（目前仅 selectedId）。 */
  function updateSelectCard(cardId: string, patch: Partial<AgentSelectCard>): void {
    setEntries((prev) =>
      prev.map((entry) =>
        entry.kind === 'select' && entry.data.id === cardId
          ? { kind: 'select', data: { ...entry.data, ...patch } }
          : entry,
      ),
    );
  }

  /**
   * 用户从可选项卡片中点选：标记已选 + 构造"我选择{label}"消息并发送。
   * 后端按 card.replyTemplate 解析，把上一轮下发的 items 配回 doctor_id 等字段。
   * @param card 可选项卡片
   * @param item 用户点选的项
   */
  const selectOption = useCallback(
    (card: AgentSelectCard, item: AgentSelectItem) => {
      // 已点选过则忽略：避免重复发送或覆盖原选择
      if (card.selectedId) return;
      updateSelectCard(card.id, { selectedId: item.id });
      const text = card.replyTemplate.replace('{label}', item.label);
      send(text);
    },
    [send],
  );

  /** 记录本地选择，确认前不向 Agent 发送任何业务编号。 */
  const selectRecordPicker = useCallback((cardId: string, recordId: number) => {
    setEntries((prev) => prev.map((entry) => entry.kind === 'record_picker' && entry.data.id === cardId
      ? { kind: 'record_picker', data: { ...entry.data, selectedId: recordId } }
      : entry));
  }, []);

  /** 更新记录选择卡状态，防止确认或取消后重复操作。 */
  const updateRecordPicker = useCallback((cardId: string, status: AgentRecordPickerCard['status']) => {
    setEntries((prev) => prev.map((entry) => entry.kind === 'record_picker' && entry.data.id === cardId
      ? { kind: 'record_picker', data: { ...entry.data, status } }
      : entry));
  }, []);

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

      // 设置目标 session_id
      saveAgentSessionId(targetSessionId);
      setSessionId(targetSessionId);

      try {
        // 获取历史消息与持久化的交互卡片（2026-08-10 卡片持久化）
        const { messages, cards } = await getSessionMessages(targetSessionId);

        // 历史消息映射为纯文本气泡
        const messageEntries: AgentEntry[] = messages.map((msg) => ({
          kind: 'message' as const,
          data: {
            id: genId(msg.role === 'user' ? 'u' : 'a'),
            role: msg.role as 'user' | 'assistant',
            content: msg.content,
            createdAt: Date.now(),
          },
        }));

        // 历史卡片按类型还原（消息在前、卡片在后），复用实时渲染组件
        const cardEntries = buildHistoryCardEntries(cards);

        setEntries([...messageEntries, ...cardEntries]);
        setConnection('idle');
      } catch (err) {
        setConnection('error');
        setErrorMessage((err as Error).message || '加载历史消息失败');
      }
    },
    [cancel],
  );

  return {
    entries,
    connection,
    errorMessage,
    sessionId,
    isStreaming: connection === 'connecting' || connection === 'streaming',
    send,
    confirm,
    selectOption,
    selectRecordPicker,
    updateRecordPicker,
    cancel,
    retry,
    reset,
    loadSession,
  };
}
