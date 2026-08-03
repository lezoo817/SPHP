import type { LoginData, LoginUser, TokenPair } from '../typings/api';

const SESSION_KEY = 'sphp_c_session';
const REMEMBERED_ACCOUNT_KEY = 'sphp_c_remembered_account';

/** 浏览器会话中保存的最小登录信息。 */
export interface SessionState extends TokenPair {
  user: LoginUser;
  loginAt: string;
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
  window.sessionStorage.setItem(SESSION_KEY, JSON.stringify({ ...data, loginAt: 'loginAt' in data ? data.loginAt : new Date().toISOString() }));
}

/** 使用刷新接口返回的新 Token 对覆盖旧值。 */
export function replaceTokenPair(tokens: TokenPair): void {
  const current = getSession();
  if (current) saveSession({ ...current, ...tokens });
}

/** 清除会话级 Token 和登录用户信息。 */
export function clearSession(): void {
  if (typeof window !== 'undefined') window.sessionStorage.removeItem(SESSION_KEY);
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
