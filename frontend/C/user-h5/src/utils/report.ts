import type { ReportItem } from '../typings/api';

/** 报告列表的日期筛选范围。 */
export interface ReportDateRange {
  /** 筛选开始日期，格式为 YYYY-MM-DD。 */
  startDate: string;
  /** 筛选结束日期，格式为 YYYY-MM-DD。 */
  endDate: string;
}

/**
 * 生成以指定日期为结束的最近报告查询范围。
 * @param days 最近天数
 * @param now 便于测试注入的当前时间
 * @returns 可直接回填 date 输入框的起止日期
 */
export function getRecentReportRange(days: number, now = new Date()): ReportDateRange {
  const end = new Date(now);
  const start = new Date(now);
  // 最近 N 天包含当天，开始日期向前回退 N - 1 天。
  start.setDate(start.getDate() - Math.max(0, days - 1));
  return { startDate: formatDateInput(start), endDate: formatDateInput(end) };
}

/**
 * 判断报告日期范围是否有效。
 * @param range 页面当前选择的开始和结束日期
 * @returns 开始日期未晚于结束日期时返回 true
 */
export function isReportDateRangeValid({ startDate, endDate }: ReportDateRange): boolean {
  return !startDate || !endDate || startDate <= endDate;
}

/**
 * 按报告完成日期筛选并保证结果仍按完成时间倒序展示。
 * @param reports 已从后端加载的报告记录
 * @param range 页面选择的日期范围
 * @returns 符合日期条件的报告副本
 */
export function filterReportsByDate(reports: ReportItem[], { startDate, endDate }: ReportDateRange): ReportItem[] {
  if (!isReportDateRangeValid({ startDate, endDate })) return [];
  return reports
    .filter((report) => {
      // ISO-8601 字符串前十位是后端东八区完成日期，避免浏览器时区转换改变筛选日。
      const completedDate = report.completedAt.slice(0, 10);
      return (!startDate || completedDate >= startDate) && (!endDate || completedDate <= endDate);
    })
    .sort((left, right) => right.completedAt.localeCompare(left.completedAt) || right.id - left.id);
}

/**
 * 合并报告分页数据并按报告 ID 去重。
 * @param current 已加载的报告记录
 * @param next 新加载的一页报告记录
 * @returns 合并后的报告记录，重复项以新数据为准
 */
export function mergeReportPages(current: ReportItem[], next: ReportItem[]): ReportItem[] {
  return Array.from(new Map([...current, ...next].map((report) => [report.id, report])).values());
}

/**
 * 判断报告解读接口异常是否表示内容尚未准备完成。
 * @param error 请求封装抛出的未知异常
 * @returns 后端返回 B0202 或 HTTP 409 时返回 true
 */
export function isReportInterpretationPending(error: unknown): boolean {
  return typeof error === 'object' && error !== null
    && (('code' in error && error.code === 'B0202') || ('status' in error && error.status === 409));
}

/** 将本地日期格式化为 HTML date 输入框需要的 YYYY-MM-DD。 */
function formatDateInput(value: Date): string {
  const year = value.getFullYear();
  const month = String(value.getMonth() + 1).padStart(2, '0');
  const day = String(value.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}
