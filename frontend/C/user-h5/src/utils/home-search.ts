import type { Department } from '../typings/api';

/**
 * 选择科室浏览页初始展示的科室。
 * @param departments 当前医院返回的科室列表
 * @returns 列表第一个科室；无科室时返回 undefined
 */
export function resolveInitialDepartment(departments: Department[]): Department | undefined {
  return departments[0];
}

/**
 * 判断关键词是否可以发起医生和科室搜索。
 * @param keyword 用户输入的原始关键词
 * @returns 去除空白后是否存在有效搜索内容
 */
export function hasSearchKeyword(keyword: string): boolean {
  return Boolean(keyword.trim());
}
