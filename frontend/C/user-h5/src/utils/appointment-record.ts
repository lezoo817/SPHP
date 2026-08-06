import type { Appointment } from '../typings/api';

/** 就诊记录页面可切换的记录分类。 */
export type AppointmentRecordTab = 'COMPLETED' | 'INVALID';

/** 就诊记录页面的日期筛选范围。 */
export interface AppointmentRecordDateRange {
  /** 筛选开始日期，格式为 YYYY-MM-DD。 */
  startDate: string;
  /** 筛选结束日期，格式为 YYYY-MM-DD。 */
  endDate: string;
}

/**
 * 生成以指定日期为结束的最近就诊记录范围。
 * @param days 最近天数
 * @param now 便于测试注入的当前时间
 * @returns 可直接回填 date 输入框的起止日期
 */
export function getRecentAppointmentRecordRange(days: number, now = new Date()): AppointmentRecordDateRange {
  const end = new Date(now);
  const start = new Date(now);
  // 最近 N 天包含当天，开始日期向前回退 N - 1 天。
  start.setDate(start.getDate() - Math.max(0, days - 1));
  return { startDate: formatDateInput(start), endDate: formatDateInput(end) };
}

/**
 * 判断就诊记录的日期范围是否有效。
 * @param range 页面当前选择的起止日期
 * @returns 开始日期未晚于结束日期时返回 true
 */
export function isAppointmentRecordDateRangeValid({ startDate, endDate }: AppointmentRecordDateRange): boolean {
  return !startDate || !endDate || startDate <= endDate;
}

/**
 * 判断挂号订单是否属于当前记录 Tab。
 * @param appointment 服务端返回的挂号订单
 * @param tab 页面当前选择的记录分类
 * @returns 归类匹配时返回 true
 */
export function matchesAppointmentRecordTab(appointment: Appointment, tab: AppointmentRecordTab): boolean {
  // 已支付但未接诊的过期订单由后端派生为 NO_SHOW，和取消订单统一归入失效记录。
  return tab === 'COMPLETED' ? appointment.status === 'COMPLETED' : appointment.status === 'NO_SHOW' || appointment.status === 'CANCELLED';
}

/**
 * 按预约开始日期筛选并保持记录倒序。
 * @param appointments 当前页面已加载的挂号订单
 * @param range 页面选择的日期范围
 * @returns 符合日期条件的挂号订单副本
 */
export function filterAppointmentRecordsByDate(appointments: Appointment[], { startDate, endDate }: AppointmentRecordDateRange): Appointment[] {
  if (!isAppointmentRecordDateRangeValid({ startDate, endDate })) return [];
  return appointments
    .filter((appointment) => {
      // 列表接口返回东八区 ISO 时间，直接按日期前缀筛选可避免浏览器时区换算改变预约日。
      const appointmentDate = appointment.startTime.slice(0, 10);
      return (!startDate || appointmentDate >= startDate) && (!endDate || appointmentDate <= endDate);
    })
    .sort((left, right) => right.startTime.localeCompare(left.startTime) || right.id - left.id);
}

/**
 * 合并就诊记录分页数据并按订单 ID 去重。
 * @param current 已加载的记录
 * @param next 新加载的一页记录
 * @returns 重复记录以最新一页数据为准的合并结果
 */
export function mergeAppointmentRecordPages(current: Appointment[], next: Appointment[]): Appointment[] {
  return Array.from(new Map([...current, ...next].map((appointment) => [appointment.id, appointment])).values());
}

/** 将本地日期格式化为 HTML date 输入框需要的 YYYY-MM-DD。 */
function formatDateInput(value: Date): string {
  const year = value.getFullYear();
  const month = String(value.getMonth() + 1).padStart(2, '0');
  const day = String(value.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}
