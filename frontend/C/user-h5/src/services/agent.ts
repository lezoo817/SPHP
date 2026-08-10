/**
 * Agent 服务层：流式对话与 L2 确认回调。
 *
 * 直连 Python Agent（:8081），与 C 端 Java 业务接口（/api/c/v1）分离。
 * 对话使用 Fetch 响应流读取七类 SSE 事件，不使用浏览器原生 EventSource。
 */
import { getSession, isSessionTokenExpired, redirectToLogin } from '../models/session';
import { refreshSessionSync } from './request';
import { AGENT_BASE_URL, AGENT_SCOPE } from '../constants/agent';
import type {
  AgentChatContext,
  AgentChatRequest,
  AgentConfirmData,
  AgentConfirmRequest,
  AgentHistoryCard,
  AgentHistoryMessage,
  AgentSession,
  AgentSessionHistory,
  AgentSseEvent,
} from '../typings/agent';
import type { ApiResponse } from '../typings/api';

/** SSE 解析回调：每解析出一条事件触发一次。 */
export type SseEventHandler = (event: AgentSseEvent) => void;

/** 终止信号：调用 AbortController.abort() 可中断流读取。 */
export interface ChatStreamHandle {
  /** 中断当前流式请求 */
  abort: () => void;
}

/** 统一访问令牌头。 */
function authHeaders(): Record<string, string> {
  const session = getSession();
  return session ? { Authorization: `Bearer ${session.accessToken}` } : {};
}

/**
 * 确保本地 Access Token 有效，必要时同步刷新一次。
 * @returns 有效访问令牌；会话不可用时返回 null
 */
async function ensureAccessToken(): Promise<string | null> {
  const session = getSession();
  if (!session) return null;
  if (isSessionTokenExpired(session)) {
    const ok = await refreshSessionSync(session.refreshToken);
    if (!ok) return null;
    return getSession()?.accessToken || null;
  }
  return session.accessToken;
}

/**
 * 发起流式对话（POST /api/chat/stream）。
 *
 * 使用 Fetch 读取响应体流，按 `\n\n` 切分 SSE 帧，解析 `event:` 与 `data:`
 * 字段，通过 onEvent 回调逐条上抛七类事件。异常或中断时调用 onError。
 *
 * @param content 用户输入文本
 * @param onEvent SSE 事件回调
 * @param onError 流读取异常回调
 * @param options 可选会话 ID、上下文、终止信号
 * @returns 流句柄，可调用 abort() 中断
 */
export function chatStream(
  content: string,
  onEvent: SseEventHandler,
  onError: (message: string, code?: string) => void,
  options: { sessionId?: string; context?: AgentChatContext; signal?: AbortSignal } = {},
): ChatStreamHandle {
  const controller = new AbortController();
  // 外部 signal 中断时同步终止内部 controller
  if (options.signal) {
    if (options.signal.aborted) controller.abort();
    else options.signal.addEventListener('abort', () => controller.abort(), { once: true });
  }

  void (async () => {
    const token = await ensureAccessToken();
    if (!token) {
      redirectToLogin();
      onError('登录状态已失效，请重新登录', 'AUTH_EXPIRED');
      return;
    }

    const body: AgentChatRequest = {
      content,
      scope: AGENT_SCOPE,
      ...(options.sessionId ? { session_id: options.sessionId } : {}),
      ...(options.context ? { context: options.context } : {}),
    };

    let response: Response;
    try {
      response = await fetch(`${AGENT_BASE_URL}/api/chat/stream`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'text/event-stream',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify(body),
        signal: controller.signal,
      });
    } catch (err) {
      if ((err as Error)?.name === 'AbortError') return;
      onError('网络连接失败，请稍后重试', 'NETWORK_ERROR');
      return;
    }

    // HTTP 层鉴权失败：401 时清理登录态并跳转登录
    if (response.status === 401) {
      redirectToLogin();
      onError('登录已失效，请重新登录', 'AUTH_EXPIRED');
      return;
    }
    // 限流 / 参数错误等非流式响应：尝试解析统一信封
    if (!response.ok || !response.body) {
      const message = await extractErrorMessage(response);
      onError(message, response.status === 429 ? 'RATE_LIMITED' : 'SERVER_ERROR');
      return;
    }

    try {
      await readSseStream(response.body, onEvent);
    } catch (err) {
      if ((err as Error)?.name === 'AbortError') return;
      onError('对话连接中断，请重试', 'STREAM_ERROR');
    }
  })();

  return { abort: () => controller.abort() };
}

/**
 * 读取 ReadableStream 并按 SSE 帧边界解析事件。
 *
 * SSE 帧格式：`event: <name>\ndata: <json>\n\n`，按 `\n\n` 切分。
 * @param stream 响应体流
 * @param onEvent 事件回调
 */
async function readSseStream(stream: ReadableStream<Uint8Array>, onEvent: SseEventHandler): Promise<void> {
  const reader = stream.getReader();
  const decoder = new TextDecoder('utf-8');
  let buffer = '';

  // eslint-disable-next-line no-constant-condition
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });

    // 按空行（\n\n）切分完整帧，剩余不完整部分留在 buffer
    let separatorIndex = buffer.indexOf('\n\n');
    while (separatorIndex !== -1) {
      const frame = buffer.slice(0, separatorIndex);
      buffer = buffer.slice(separatorIndex + 2);
      const parsed = parseSseFrame(frame);
      if (parsed) onEvent(parsed);
      separatorIndex = buffer.indexOf('\n\n');
    }
  }
  // 处理流末尾可能残留的最后一帧（无结尾 \n\n）
  if (buffer.trim()) {
    const parsed = parseSseFrame(buffer);
    if (parsed) onEvent(parsed);
  }
}

/**
 * 解析单帧 SSE 文本为结构化事件。
 * @param frame 单帧文本（不含结尾空行）
 * @returns 解析后的事件，格式异常时返回 null
 */
function parseSseFrame(frame: string): AgentSseEvent | null {
  let eventName = 'message';
  const dataLines: string[] = [];
  for (const line of frame.split('\n')) {
    if (line.startsWith('event:')) eventName = line.slice(6).trim();
    else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim());
  }
  if (!dataLines.length) return null;
  try {
    const data = JSON.parse(dataLines.join('\n'));
    // 校验事件类型并归一化 observation.status（后端字段为 success 布尔）
    switch (eventName) {
      case 'message':
        return { event: 'message', data };
      case 'thought':
        return { event: 'thought', data };
      case 'action':
        return { event: 'action', data };
      case 'observation': {
        const obs = { ...data };
        if (typeof obs.success === 'boolean') {
          obs.status = obs.success ? 'success' : 'error';
        } else if (typeof obs.status !== 'string') {
          obs.status = 'success';
        }
        return { event: 'observation', data: obs };
      }
      case 'card':
        return { event: 'card', data };
      case 'action_card':
        return { event: 'action_card', data };
      case 'record_picker':
        return { event: 'record_picker', data };
      case 'options':
        return { event: 'options', data };
      case 'error':
        return { event: 'error', data };
      case 'done':
        return { event: 'done', data };
      default:
        return null;
    }
  } catch {
    return null;
  }
}

/** 从非流式错误响应中提取用户可读信息。 */
async function extractErrorMessage(response: Response): Promise<string> {
  try {
    const payload = (await response.json()) as ApiResponse<unknown>;
    return payload.message || '对话请求失败，请稍后重试';
  } catch {
    return response.status === 429 ? '对话请求过于频繁，请稍后重试' : '服务暂时不可用，请稍后重试';
  }
}

/**
 * 获取历史会话列表（GET /api/chat/sessions）。
 *
 * 返回当前用户的所有 C 端 AI 会话，按最后更新时间倒序。
 * @returns 会话列表
 */
export async function getSessions(): Promise<AgentSession[]> {
  const token = await ensureAccessToken();
  if (!token) {
    redirectToLogin();
    throw new Error('登录状态已失效，请重新登录');
  }

  let response: Response;
  try {
    response = await fetch(`${AGENT_BASE_URL}/api/chat/sessions`, {
      method: 'GET',
      headers: { Authorization: `Bearer ${token}` },
    });
  } catch {
    throw new Error('网络连接失败，请稍后重试');
  }

  if (response.status === 401) {
    redirectToLogin();
    throw new Error('登录已失效，请重新登录');
  }

  let payload: ApiResponse<{ sessions: AgentSession[] }>;
  try {
    payload = (await response.json()) as ApiResponse<{ sessions: AgentSession[] }>;
  } catch {
    throw new Error('获取会话列表失败，请稍后重试');
  }

  if (payload.code === '00000' && payload.data?.sessions) {
    return payload.data.sessions;
  }

  throw new Error(payload.message || '获取会话列表失败');
}

/**
 * 删除指定历史会话（DELETE /api/chat/sessions/{session_id}）。
 * @param sessionId 会话 ID
 */
export async function deleteSession(sessionId: string): Promise<void> {
  const token = await ensureAccessToken();
  if (!token) {
    redirectToLogin();
    throw new Error('登录状态已失效，请重新登录');
  }

  let response: Response;
  try {
    response = await fetch(`${AGENT_BASE_URL}/api/chat/sessions/${encodeURIComponent(sessionId)}`, {
      method: 'DELETE',
      headers: { Authorization: `Bearer ${token}` },
    });
  } catch {
    throw new Error('网络连接失败，请稍后重试');
  }

  if (response.status === 401) {
    redirectToLogin();
    throw new Error('登录已失效，请重新登录');
  }

  let payload: ApiResponse<unknown>;
  try {
    payload = (await response.json()) as ApiResponse<unknown>;
  } catch {
    throw new Error('删除会话失败，请稍后重试');
  }

  if (payload.code !== '00000') {
    throw new Error(payload.message || '删除会话失败');
  }
}

/**
 * 获取指定会话的历史消息（GET /api/chat/sessions/{session_id}/messages）。
 * @param sessionId 会话 ID
 * @returns 历史会话内容：纯文本消息 + 持久化的交互卡片（payload 与 SSE 实时事件一致）
 */
export async function getSessionMessages(sessionId: string): Promise<AgentSessionHistory> {
  const token = await ensureAccessToken();
  if (!token) {
    redirectToLogin();
    throw new Error('登录状态已失效，请重新登录');
  }

  let response: Response;
  try {
    response = await fetch(`${AGENT_BASE_URL}/api/chat/sessions/${encodeURIComponent(sessionId)}/messages`, {
      method: 'GET',
      headers: { Authorization: `Bearer ${token}` },
    });
  } catch {
    throw new Error('网络连接失败，请稍后重试');
  }

  if (response.status === 401) {
    redirectToLogin();
    throw new Error('登录已失效，请重新登录');
  }

  let payload: ApiResponse<{ messages: AgentHistoryMessage[]; cards?: AgentHistoryCard[] }>;
  try {
    payload = (await response.json()) as ApiResponse<{
      messages: AgentHistoryMessage[];
      cards?: AgentHistoryCard[];
    }>;
  } catch {
    throw new Error('获取历史消息失败，请稍后重试');
  }

  if (payload.code === '00000' && payload.data?.messages) {
    // cards 缺省（旧后端）兜底为空数组，保证前端渲染兼容
    return { messages: payload.data.messages, cards: payload.data.cards ?? [] };
  }

  throw new Error(payload.message || '获取历史消息失败');
}

/**
 * L2 确认回调（POST /api/chat/confirm）。
 *
 * 独立同步 JSON 请求，携带 confirm_token + session_id。成功（code=00000）
 * 返回 action_result 与 message；失败时抛出含错误码的 ApiError。
 *
 * @param payload 确认令牌与会话 ID
 * @returns 确认结果数据
 */
export async function confirmCard(payload: AgentConfirmRequest): Promise<AgentConfirmData> {
  const token = await ensureAccessToken();
  if (!token) {
    redirectToLogin();
    throw new Error('登录状态已失效，请重新登录');
  }

  let response: Response;
  try {
    response = await fetch(`${AGENT_BASE_URL}/api/chat/confirm`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify(payload),
    });
  } catch {
    throw new Error('网络连接失败，请稍后重试');
  }

  // 401 鉴权失败：清理登录态并跳转
  if (response.status === 401) {
    redirectToLogin();
    throw new Error('登录已失效，请重新登录');
  }

  let payloadJson: ApiResponse<AgentConfirmData>;
  try {
    payloadJson = (await response.json()) as ApiResponse<AgentConfirmData>;
  } catch {
    throw new Error('确认请求响应异常，请稍后重试');
  }

  if (payloadJson.code === '00000') {
    return payloadJson.data || { message: '操作成功' };
  }

  // 业务错误：抛出含 code 的异常，由调用方按错误码处理卡片状态
  const error = new Error(payloadJson.message || '确认请求失败') as Error & { code?: string };
  error.code = payloadJson.code;
  throw error;
}
