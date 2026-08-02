import { clearSession, getSession, replaceTokenPair } from '../models/session';
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

/** 发送 C 端 API 请求并统一处理 Token 与业务响应。 */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { body, headers, skipAuth, skipRefresh, ...init } = options;
  const session = getSession();
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

  // Access Token 失效时仅刷新一次，避免请求递归重试。
  if (!skipAuth && !skipRefresh && (response.status === 401 || payload.code === 'A0301') && session) {
    const refreshed = await refreshSession(session.refreshToken);
    if (refreshed) return request<T>(path, { ...options, skipRefresh: true });
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

/** 调用刷新接口并覆盖本地 Token 对，失败时结束登录会话。 */
async function refreshSession(refreshToken: string): Promise<boolean> {
  try {
    const result = await request<TokenPair>('/c/v1/auth/token/refresh', {
      method: 'POST', body: { refreshToken }, skipAuth: true, skipRefresh: true,
    });
    replaceTokenPair(result);
    return true;
  } catch {
    clearSession();
    window.location.assign('/login');
    return false;
  }
}
