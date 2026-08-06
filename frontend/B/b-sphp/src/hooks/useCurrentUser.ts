/**
 * 当前登录用户 Hook。
 *
 * 基于 Umi 全局 initialState（app.ts getInitialState 解析 token 注入），
 * 供各页面判断角色/科室等权限时复用，避免各处重复 useModel('@@initialState')。
 */
import { useModel } from '@umijs/max';

/** 获取当前登录用户信息；未登录时为 undefined。 */
export function useCurrentUser(): API.User | undefined {
  const { initialState } = useModel('@@initialState');
  return initialState?.currentUser;
}

/** 当前用户是否拥有指定角色（如 'ADMIN'）。 */
export function useHasRole(role: string): boolean {
  const user = useCurrentUser();
  return user?.roles?.includes(role) ?? false;
}
