import type { CaptchaData, LoginData, TokenPair } from '../typings/api';
import { request } from './request';

/** 获取注册所需的一次性图形验证码。 */
export function getCaptcha(): Promise<CaptchaData> {
  return request('/c/v1/auth/captcha', { method: 'GET', skipAuth: true });
}

/** 注册 C 端账号。 */
export function register(payload: { account: string; password: string; challengeId: string; captchaCode: string }): Promise<{ userId: number; account: string }> {
  return request('/c/v1/auth/register', { method: 'POST', body: payload, skipAuth: true });
}

/** 使用账号和密码获取 C 端 Token 对。 */
export function login(payload: { account: string; password: string }): Promise<LoginData> {
  return request('/c/v1/auth/login', { method: 'POST', body: payload, skipAuth: true });
}

/** 撤销当前登录会话。 */
export function logout(refreshToken: string): Promise<{ loggedOut: boolean }> {
  return request('/c/v1/auth/logout', { method: 'POST', body: { refreshToken } });
}

/** 修改当前账号登录密码。 */
export function changePassword(payload: { oldPassword: string; newPassword: string }): Promise<{ passwordChanged: boolean }> {
  return request('/c/v1/auth/password', { method: 'PUT', body: payload });
}
