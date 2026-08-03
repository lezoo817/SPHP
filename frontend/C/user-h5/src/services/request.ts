import { getSession, isSessionTokenExpired, redirectToLogin, replaceTokenPair } from '../models/session';
import type { ApiResponse, TokenPair } from '../typings/api';

export const API_BASE_URL = 'http://localhost:8080/api';

/** 可携带业务码和链路追踪号的请求异常。 */
export class ApiError extends Error {
  code?: string;
  traceId?: string;
  status?: number;

  /** 创建统一接口异常。 */
  constructor(message: string, options: { code?: string; traceId?: string; status?: number } = {}) {
    super(message);
    Object.assign(this, options);
  }
}

interface RequestOptions extends Omit<RequestInit, 'body' | 'headers'> {
  body?: unknown;
  headers?: Record<string, string>;
  skipAuth?: boolean;
  skipRefresh?: boolean;
}

/**
 * 判断响应是否表示当前登录会话无效。
 * @param status HTTP 状态码
 * @param code 后端业务码
 * @returns 会话失效时返回 true
 */
export function isAuthenticationFailure(status: number, code?: string): boolean {
  return status === 401 || code === 'A0301';
}

/**
 * 结束会话并抛出可供页面捕获的认证异常。
 * @param message 服务端或本地生成的失效提示
 * @param options 错误码和状态信息
 * @returns 不返回，始终抛出异常
 */
function failAuthentication(message: string, options: { code?: string; traceId?: string; status?: number } = {}): never {
  redirectToLogin();
  throw new ApiError(message || '登录状态已失效，请重新登录', options);
}

/** 发送 C 端 API 请求并统一处理 Token 刷新与失效跳转。 */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { body, headers, skipAuth, skipRefresh, ...init } = options;
  const session = getSession();

  // 登录、注册和刷新接口不参与全局登录态判断。
  if (!skipAuth && !session) return failAuthentication('登录状态已失效，请重新登录', { status: 401, code: 'A0301' });
  if (!skipAuth && session && isSessionTokenExpired(session) && !skipRefresh) {
    const refreshed = await refreshSession(session.refreshToken);
    if (refreshed) return request<T>(path, { ...options, skipRefresh: true });
    return failAuthentication('登录状态已失效，请重新登录', { status: 401, code: 'A0301' });
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(skipAuth || !session ? {} : { Authorization: `Bearer ${session.accessToken}` }),
      ...headers,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const payload = await parseResponse<T>(response);
  if (payload.code === '00000') return payload.data;

  if (!skipAuth && isAuthenticationFailure(response.status, payload.code)) {
    // Access Token 失效仅刷新一次，刷新后仍失败说明会话已不可用。
    if (!skipRefresh && session) {
      const refreshed = await refreshSession(session.refreshToken);
      if (refreshed) return request<T>(path, { ...options, skipRefresh: true });
    }
    return failAuthentication(payload.message, { code: payload.code, traceId: payload.traceId, status: response.status });
  }
  throw new ApiError(payload.message, { code: payload.code, traceId: payload.traceId, status: response.status });
}

/** 解析后端统一响应，兼容无效 JSON 的网络异常。 */
async function parseResponse<T>(response: Response): Promise<ApiResponse<T>> {
  try {
    return await response.json() as ApiResponse<T>;
  } catch {
    throw new ApiError('服务暂时不可用，请稍后重试', { status: response.status });
  }
}

/** 调用刷新接口并覆盖本地 Token 对，失败时交由调用方统一结束会话。 */
async function refreshSession(refreshToken: string): Promise<boolean> {
  try {
    const result = await request<TokenPair>('/c/v1/auth/token/refresh', {
      method: 'POST', body: { refreshToken }, skipAuth: true, skipRefresh: true,
    });
    replaceTokenPair(result);
    return true;
  } catch {
    return false;
  }
}

/**
 * 同步刷新当前登录会话的 Access Token（供 Agent 流式请求复用）。
 *
 * Agent 直连 Python 服务，不经过 request 统一封装，因此需要在发起流式请求前
 * 独立完成一次刷新判断。成功返回 true，会话不可用时返回 false。
 * @param refreshToken 当前刷新令牌
 * @returns 刷新成功且仍处于登录态时返回 true
 */
export async function refreshSessionSync(refreshToken: string): Promise<boolean> {
  return refreshSession(refreshToken);
}
