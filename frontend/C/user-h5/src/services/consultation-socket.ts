import { API_BASE_URL } from './request';
import { getSession } from '../models/session';

/** 在线问诊实时消息载荷。 */
export interface ConsultationSocketMessage { consultationId: number; messageId: number; senderType: 'PATIENT' | 'DOCTOR' | 'SYSTEM'; content: string; createdAt: string; }
/** 可释放的实时连接。 */
export interface ConsultationSocketConnection { /** 关闭连接并停止重连。 */ deactivate(): void; }

/** 构造 STOMP 协议帧。 */
function frame(command: string, headers: Record<string, string> = {}, body = ''): string { return `${command}\n${Object.entries(headers).map(([key, value]) => `${key}:${value}`).join('\n')}\n\n${body}\0`; }
/** 读取 STOMP 消息帧正文。 */
function parseMessage(raw: string): ConsultationSocketMessage | undefined { if (!raw.startsWith('MESSAGE\n')) return undefined; const separator = raw.indexOf('\n\n'); if (separator < 0) return undefined; try { return JSON.parse(raw.slice(separator + 2).replace(/\0$/, '')) as ConsultationSocketMessage; } catch { return undefined; } }
/** 将 API 地址转换为 WebSocket STOMP 地址。 */
function resolveSocketUrl(): string { const apiUrl = new URL(API_BASE_URL, window.location.origin); apiUrl.protocol = apiUrl.protocol === 'https:' ? 'wss:' : 'ws:'; apiUrl.pathname = `${apiUrl.pathname.replace(/\/$/, '')}/ws/consultation`; return apiUrl.toString(); }

/**
 * 创建 C端在线问诊私有 WebSocket 订阅，断线时以指数退避重连。
 *
 * @param onMessage 收到实时消息后的回调
 * @returns 可关闭的 STOMP 连接
 */
export function createConsultationSocket(onMessage: (message: ConsultationSocketMessage) => void): ConsultationSocketConnection | undefined {
  const token = getSession()?.accessToken;
  if (!token) return undefined;
  let socket: WebSocket | undefined; let reconnectTimer: number | undefined; let heartbeatTimer: number | undefined; let retry = 0; let active = true;
  const connect = () => {
    if (!active) return;
    socket = new WebSocket(resolveSocketUrl());
    socket.onopen = () => socket?.send(frame('CONNECT', { Authorization: `Bearer ${token}`, 'X-Client-Type': 'C', 'heart-beat': '10000,10000' }));
    socket.onmessage = (event) => { const raw = String(event.data); if (raw.startsWith('CONNECTED')) { retry = 0; socket?.send(frame('SUBSCRIBE', { id: 'consultation-message', destination: '/user/queue/consultation-message', ack: 'auto' })); heartbeatTimer = window.setInterval(() => socket?.readyState === WebSocket.OPEN && socket.send('\n'), 10000); } raw.split('\0').map(parseMessage).filter((item): item is ConsultationSocketMessage => Boolean(item)).forEach(onMessage); };
    socket.onclose = () => { if (heartbeatTimer) window.clearInterval(heartbeatTimer); if (!active) return; reconnectTimer = window.setTimeout(connect, Math.min(1000 * 2 ** retry++, 30000)); };
  };
  connect();
  return { deactivate: () => { active = false; if (reconnectTimer) window.clearTimeout(reconnectTimer); if (heartbeatTimer) window.clearInterval(heartbeatTimer); socket?.close(); } };
}
