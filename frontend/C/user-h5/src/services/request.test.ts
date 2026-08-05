import { beforeEach, describe, expect, it, vi } from 'vitest';

const sessionMock = vi.hoisted(() => ({
  current: null as { accessToken: string; refreshToken: string; expiresIn: number; user: { id: number; account: string }; loginAt: string; accessTokenIssuedAt: string } | null,
  getSession: vi.fn(),
  isSessionTokenExpired: vi.fn(),
  redirectToLogin: vi.fn(),
  replaceTokenPair: vi.fn(),
}));

vi.mock('../models/session', () => ({
  getSession: sessionMock.getSession,
  isSessionTokenExpired: sessionMock.isSessionTokenExpired,
  redirectToLogin: sessionMock.redirectToLogin,
  replaceTokenPair: sessionMock.replaceTokenPair,
}));

import { isAuthenticationFailure, request } from './request';
import { clearRequestCache } from '../query/request-cache';

/** 创建符合后端统一响应结构的测试 Response。 */
function response(code: string, data: unknown, status = 200): Response {
  return new Response(JSON.stringify({ code, message: code === '00000' ? '成功' : '登录失效', data, traceId: 'trace-test' }), { status, headers: { 'Content-Type': 'application/json' } });
}

describe('统一请求认证失效处理', () => {
  beforeEach(() => {
    clearRequestCache();
    sessionMock.current = { accessToken: 'old-access', refreshToken: 'refresh', expiresIn: 900, user: { id: 1, account: 'patient' }, loginAt: '2026-08-03T00:00:00.000Z', accessTokenIssuedAt: '2026-08-03T00:00:00.000Z' };
    sessionMock.getSession.mockImplementation(() => sessionMock.current);
    sessionMock.isSessionTokenExpired.mockReturnValue(false);
    sessionMock.redirectToLogin.mockReset();
    sessionMock.replaceTokenPair.mockImplementation((tokens: { accessToken: string; refreshToken: string; expiresIn: number }) => { if (sessionMock.current) sessionMock.current = { ...sessionMock.current, ...tokens, accessTokenIssuedAt: '2026-08-03T00:01:00.000Z' }; });
    vi.stubGlobal('fetch', vi.fn());
  });

  it('401 后刷新一次令牌并重试原请求', async () => {
    const fetchMock = vi.mocked(fetch);
    fetchMock.mockResolvedValueOnce(response('A0301', null, 401)).mockResolvedValueOnce(response('00000', { accessToken: 'new-access', refreshToken: 'new-refresh', expiresIn: 900 })).mockResolvedValueOnce(response('00000', { id: 1 }));
    await expect(request<{ id: number }>('/c/v1/profile')).resolves.toEqual({ id: 1 });
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(fetchMock.mock.calls[2][1]?.headers).toMatchObject({ Authorization: 'Bearer new-access' });
    expect(sessionMock.redirectToLogin).not.toHaveBeenCalled();
  });

  it('刷新后仍返回 401 时清理会话并跳转登录', async () => {
    const fetchMock = vi.mocked(fetch);
    fetchMock.mockResolvedValueOnce(response('A0301', null, 401)).mockResolvedValueOnce(response('00000', { accessToken: 'new-access', refreshToken: 'new-refresh', expiresIn: 900 })).mockResolvedValueOnce(response('A0301', null, 401));
    await expect(request('/c/v1/profile')).rejects.toMatchObject({ code: 'A0301', status: 401 });
    expect(sessionMock.redirectToLogin).toHaveBeenCalledTimes(1);
  });

  it('无会话时不发起受保护请求', async () => {
    sessionMock.current = null;
    await expect(request('/c/v1/profile')).rejects.toMatchObject({ code: 'A0301', status: 401 });
    expect(fetch).not.toHaveBeenCalled();
    expect(sessionMock.redirectToLogin).toHaveBeenCalledTimes(1);
  });

  it('skipAuth 接口的 401 不触发全局跳转', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(response('A0301', null, 401));
    await expect(request('/c/v1/auth/login', { method: 'POST', skipAuth: true })).rejects.toMatchObject({ code: 'A0301', status: 401 });
    expect(sessionMock.redirectToLogin).not.toHaveBeenCalled();
  });

  it('识别 HTTP 401 和 A0301 业务码', () => {
    expect(isAuthenticationFailure(401)).toBe(true);
    expect(isAuthenticationFailure(200, 'A0301')).toBe(true);
    expect(isAuthenticationFailure(400, 'A0400')).toBe(false);
  });
});
