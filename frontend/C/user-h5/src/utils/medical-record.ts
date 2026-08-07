import type { MedicalRecordItem } from '../typings/api';
import type { AgentNavigationState } from '../typings/agent';

/**
 * 构建病历“AI 一键解读”跳转状态。
 * @param from 病历详情页的完整地址
 * @param consultId 服务端真实完成问诊记录 ID
 * @returns 跳转 AI 页面时使用的路由状态
 */
export function buildMedicalRecordInterpretationAgentState(from: string, consultId: number): AgentNavigationState {
  return {
    from,
    presetAction: { type: 'interpret_medical_record', consultId },
  };
}

/** 病历列表的日期筛选范围。 */
export interface MedicalRecordDateRange {
  /** 筛选开始日期，格式为 YYYY-MM-DD。 */
  startDate: string;
  /** 筛选结束日期，格式为 YYYY-MM-DD。 */
  endDate: string;
}

/**
 * 生成以指定日期为结束的最近病历查询范围。
 * @param days 最近天数
 * @param now 便于测试注入的当前时间
 * @returns 可直接回填 date 输入框的起止日期
 */
export function getRecentMedicalRecordRange(days: number, now = new Date()): MedicalRecordDateRange {
  const end = new Date(now);
  const start = new Date(now);
  // 最近 N 天包含当天，开始日期向前回退 N - 1 天。
  start.setDate(start.getDate() - Math.max(0, days - 1));
  return { startDate: formatDateInput(start), endDate: formatDateInput(end) };
}

/**
 * 判断病历日期范围是否有效。
 * @param range 页面当前选择的开始和结束日期
 * @returns 开始日期未晚于结束日期时返回 true
 */
export function isMedicalRecordDateRangeValid({ startDate, endDate }: MedicalRecordDateRange): boolean {
  return !startDate || !endDate || startDate <= endDate;
}

/**
 * 按病历完成日期筛选并保证结果仍按完成时间倒序展示。
 * @param medicalRecords 已从后端加载的病历记录
 * @param range 页面选择的日期范围
 * @returns 符合日期条件的病历副本
 */
export function filterMedicalRecordsByDate(medicalRecords: MedicalRecordItem[], { startDate, endDate }: MedicalRecordDateRange): MedicalRecordItem[] {
  if (!isMedicalRecordDateRangeValid({ startDate, endDate })) return [];
  return medicalRecords
    .filter((medicalRecord) => {
      // ISO-8601 字符串前十位是后端东八区完成日期，避免浏览器时区转换改变筛选日。
      const completedDate = medicalRecord.completedAt.slice(0, 10);
      return (!startDate || completedDate >= startDate) && (!endDate || completedDate <= endDate);
    })
    .sort((left, right) => right.completedAt.localeCompare(left.completedAt) || right.id - left.id);
}

/**
 * 合并病历分页数据并按病历 ID 去重。
 * @param current 已加载的病历记录
 * @param next 新加载的一页病历记录
 * @returns 合并后的病历记录，重复项以新数据为准
 */
export function mergeMedicalRecordPages(current: MedicalRecordItem[], next: MedicalRecordItem[]): MedicalRecordItem[] {
  return Array.from(new Map([...current, ...next].map((medicalRecord) => [medicalRecord.id, medicalRecord])).values());
}

/**
 * 根据病历完成时间和六位随机数生成仅供前端展示的报告编号。
 * @param completedAt 后端返回的病历完成时间
 * @param randomValue 可选随机数，便于测试时固定编号尾部
 * @returns 完成时间毫秒时间戳与六位随机数拼接的编号
 */
export function createMedicalRecordDisplayNumber(completedAt?: string, randomValue = Math.floor(Math.random() * 1_000_000)): string {
  const timestamp = completedAt ? Date.parse(completedAt) : Number.NaN;
  // 病历详情应始终有完成时间，异常数据不生成误导性的时间戳编号。
  if (!Number.isFinite(timestamp)) return '暂未提供';
  const suffix = Number.isFinite(randomValue) ? Math.max(0, Math.min(999999, Math.trunc(randomValue))) : 0;
  return `${timestamp}${String(suffix).padStart(6, '0')}`;
}

/**
 * 构建旧报告链接跳转到病历报告页面的目标路径。
 * @param consultId 旧链接中的报告 ID，对应病历 consultId
 * @param search 需要保留的来源、就诊人和日期查询参数
 * @returns 新病历报告页面路径
 */
export function buildLegacyReportRedirectPath(consultId: string | undefined, search: string): string {
  return `${consultId ? `/medical-records/${encodeURIComponent(consultId)}` : '/medical-records'}${search}`;
}

/** 将本地日期格式化为 HTML date 输入框需要的 YYYY-MM-DD。 */
function formatDateInput(value: Date): string {
  const year = value.getFullYear();
  const month = String(value.getMonth() + 1).padStart(2, '0');
  const day = String(value.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}
