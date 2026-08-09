import type { LoginData, LoginUser, TokenPair } from '../typings/api';
import { clearRequestCache } from '../query/request-cache';
import { clearDismissedExpiredHealthTodos } from './expired-health-todo';
import { clearMedicationHealthTodoStates } from './medication-health-todo';
import { clearSelection } from './selection';

const SESSION_KEY = 'sphp_c_session';
const REMEMBERED_ACCOUNT_KEY = 'sphp_c_remembered_account';

/** 浏览器会话中保存的最小登录信息。 */
export interface SessionState extends TokenPair {
  user: LoginUser;
  loginAt: string;
  accessTokenIssuedAt?: string;
}

/** 读取当前浏览器会话，服务端渲染场景返回空值。 */
export function getSession(): SessionState | null {
  if (typeof window === 'undefined') return null;
  const value = window.sessionStorage.getItem(SESSION_KEY);
  if (!value) return null;
  try {
    return JSON.parse(value) as SessionState;
  } catch {
    clearSession();
    return null;
  }
}

/** 保存登录成功后的 Token 对和用户摘要。 */
export function saveSession(data: LoginData | SessionState): void {
  const now = new Date().toISOString();
  window.sessionStorage.setItem(SESSION_KEY, JSON.stringify({
    ...data,
    // loginAt 用于业务演示时间，刷新令牌时不能覆盖。
    loginAt: 'loginAt' in data ? data.loginAt : now,
    // Access Token 签发时间用于本地判断有效期。
    accessTokenIssuedAt: 'accessTokenIssuedAt' in data && data.accessTokenIssuedAt ? data.accessTokenIssuedAt : now,
  }));
}

/** 使用刷新接口返回的新 Token 对覆盖旧值。 */
export function replaceTokenPair(tokens: TokenPair): void {
  const current = getSession();
  if (current) saveSession({ ...current, ...tokens, accessTokenIssuedAt: new Date().toISOString() });
}

/** 清除会话级 Token 和登录用户信息。 */
export function clearSession(): void {
  // 退出或账号切换时同步清除内存中的医疗和订单查询结果。
  clearRequestCache();
  // 同步清除跨页面就诊人，避免残留前一账号的医疗上下文。
  clearSelection();
  // 过期待办关闭状态仅绑定本次登录会话，避免换账号后继承旧记录。
  clearDismissedExpiredHealthTodos();
  // 本地服药确认只属于当前账号会话，退出后不可由下一账号继承。
  clearMedicationHealthTodoStates();
  if (typeof window !== 'undefined') window.sessionStorage.removeItem(SESSION_KEY);
}

/**
 * 判断当前 Access Token 是否已超过本地有效期。
 * @param session 当前会话数据
 * @param now 当前时间戳，测试时可传入固定值
 * @returns Token 缺失、格式异常或过期时返回 true
 */
export function isSessionTokenExpired(session: SessionState | null, now = Date.now()): boolean {
  if (!session?.accessToken || !session.refreshToken || !Number.isFinite(session.expiresIn) || session.expiresIn <= 0) return true;
  const issuedAt = Date.parse(session.accessTokenIssuedAt || session.loginAt);
  return !Number.isFinite(issuedAt) || issuedAt + session.expiresIn * 1000 <= now;
}

/**
 * 清除已失效会话并跳转登录页，避免重复保留受保护页面历史记录。
 */
export function redirectToLogin(): void {
  clearSession();
  if (typeof window !== 'undefined' && window.location.pathname !== '/login') window.location.replace('/login');
}

/** 保存或清除用户主动选择记住的账号文本。 */
export function saveRememberedAccount(account: string, shouldRemember: boolean): void {
  if (shouldRemember) window.localStorage.setItem(REMEMBERED_ACCOUNT_KEY, account);
  else window.localStorage.removeItem(REMEMBERED_ACCOUNT_KEY);
}

/** 读取上次选择记住的账号，不保存密码。 */
export function getRememberedAccount(): string {
  return typeof window === 'undefined' ? '' : window.localStorage.getItem(REMEMBERED_ACCOUNT_KEY) || '';
}
