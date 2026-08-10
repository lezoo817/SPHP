import { ROLE_ADMIN, ROLE_DEPT_HEAD } from '@/constants/businessStatus';

/**
 * Umi 路由级权限入口：根据当前登录用户角色数组输出页面级 access 标记。
 *
 * - isAuthenticated：已登录；
 * - isAdmin：含 ADMIN 角色（医院管理 / 药品库存 / 统计报表等页面）；
 * - isDeptHead：含 DEPT_HEAD 角色；
 * - canAudit：ADMIN 或 DEPT_HEAD（可访问待审核处方）。
 *
 * 角色字符串与后端 BRoleEnum 保持一致，统一引用 {@link ROLE_ADMIN} / {@link ROLE_DEPT_HEAD}。
 *
 * @param initialState Umi 全局初始状态（含当前用户）
 * @returns 页面级权限标记
 */
export default function access(initialState: { currentUser?: API.User }) {
  const { currentUser } = initialState;
  const roles = currentUser?.roles ?? [];

  return {
    isAuthenticated: !!currentUser,
    isAdmin: roles.includes(ROLE_ADMIN),
    isDeptHead: roles.includes(ROLE_DEPT_HEAD),
    canAudit: roles.includes(ROLE_ADMIN) || roles.includes(ROLE_DEPT_HEAD),
  };
}