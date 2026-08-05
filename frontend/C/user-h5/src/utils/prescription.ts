import type { Prescription } from '../typings/api';

/** 处方列表的日期筛选范围。 */
export interface PrescriptionDateRange {
  /** 筛选开始日期，格式为 YYYY-MM-DD。 */
  startDate: string;
  /** 筛选结束日期，格式为 YYYY-MM-DD。 */
  endDate: string;
}

/**
 * 生成以指定日期为结束的最近处方查询范围。
 * @param days 最近天数
 * @param now 便于测试注入的当前时间
 * @returns 可直接回填日期输入框的起止日期
 */
export function getRecentPrescriptionRange(days: number, now = new Date()): PrescriptionDateRange {
  const end = new Date(now);
  const start = new Date(now);
  // 最近 N 天包含当天，开始日期向前回退 N - 1 天。
  start.setDate(start.getDate() - Math.max(0, days - 1));
  return { startDate: formatDateInput(start), endDate: formatDateInput(end) };
}

/**
 * 判断处方日期范围是否有效。
 * @param range 页面当前选择的开始和结束日期
 * @returns 开始日期未晚于结束日期时返回 true
 */
export function isPrescriptionDateRangeValid({ startDate, endDate }: PrescriptionDateRange): boolean {
  return !startDate || !endDate || startDate <= endDate;
}

/**
 * 按处方开具日期筛选并保证结果按开具时间倒序展示。
 * @param prescriptions 已从后端加载的处方记录
 * @param range 页面选择的日期范围
 * @returns 符合日期条件的处方副本
 */
export function filterPrescriptionsByDate(prescriptions: Prescription[], { startDate, endDate }: PrescriptionDateRange): Prescription[] {
  if (!isPrescriptionDateRangeValid({ startDate, endDate })) return [];
  return prescriptions
    .filter((prescription) => {
      // ISO-8601 前十位保留后端东八区开具日期，避免浏览器时区换算改变筛选日。
      const issuedDate = prescription.issuedAt.slice(0, 10);
      return (!startDate || issuedDate >= startDate) && (!endDate || issuedDate <= endDate);
    })
    .sort((left, right) => right.issuedAt.localeCompare(left.issuedAt) || right.id - left.id);
}

/**
 * 合并处方分页数据并按处方 ID 去重。
 * @param current 已加载的处方记录
 * @param next 新加载的一页处方记录
 * @returns 合并后的处方记录，重复项以新数据为准
 */
export function mergePrescriptionPages(current: Prescription[], next: Prescription[]): Prescription[] {
  return Array.from(new Map([...current, ...next].map((prescription) => [prescription.id, prescription])).values());
}

/**
 * 构建“我的处方”进入详情时需要保留的页面上下文。
 * @param prescriptionId 处方编号
 * @param patientId 当前页面选择的就诊人编号
 * @param range 当前日期筛选范围
 * @returns 既有处方详情的完整跳转路径
 */
export function buildMinePrescriptionDetailPath(prescriptionId: number, patientId: number | undefined, range: PrescriptionDateRange): string {
  const search = new URLSearchParams({ source: 'mine-prescriptions', startDate: range.startDate, endDate: range.endDate });
  if (patientId && patientId > 0) search.set('patientId', String(patientId));
  return `/assistant/prescription/${prescriptionId}?${search.toString()}`;
}

/**
 * 从处方详情的查询参数恢复“我的处方”列表上下文。
 * @param search 详情页地址中的查询参数
 * @returns 处方列表返回路径
 */
export function buildMinePrescriptionListPath(search: URLSearchParams): string {
  const params = new URLSearchParams();
  ['patientId', 'startDate', 'endDate'].forEach((key) => {
    const value = search.get(key);
    if (value) params.set(key, value);
  });
  const query = params.toString();
  return `/mine/prescriptions${query ? `?${query}` : ''}`;
}

/** 将本地日期格式化为 HTML date 输入框要求的 YYYY-MM-DD。 */
function formatDateInput(value: Date): string {
  const year = value.getFullYear();
  const month = String(value.getMonth() + 1).padStart(2, '0');
  const day = String(value.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}
