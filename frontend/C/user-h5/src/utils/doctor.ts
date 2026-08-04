import type { AppointmentSlot, Doctor } from '../typings/api';

/** 医生号源页的一天展示信息。 */
export interface DoctorScheduleDate { value: string; day: string; weekday: string; }

/**
 * 生成从指定日期起的连续七天号源日期。
 * @param start 开始日期，默认当天
 * @returns 可供医生挂号页切换的日期列表
 */
export function getDoctorScheduleDates(start = new Date()): DoctorScheduleDate[] {
  return Array.from({ length: 7 }, (_, index) => {
    const date = new Date(start);
    date.setDate(date.getDate() + index);
    return {
      value: formatLocalDate(date),
      day: `${date.getMonth() + 1}/${date.getDate()}`,
      weekday: ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][date.getDay()],
    };
  });
}

/**
 * 构造医生个人挂号页地址。
 * @param doctorId 医生 ID
 * @param departmentId 医生所属科室 ID
 * @returns 可被深链接访问的医生挂号页地址
 */
export function buildDoctorPagePath(doctorId: number, departmentId?: number): string {
  return `/assistant/doctor/${doctorId}${departmentId ? `?departmentId=${departmentId}` : ''}`;
}

/**
 * 在按科室查询的医生结果中定位指定医生。
 * @param doctorId 医生 ID
 * @param records 带科室上下文的医生列表
 * @returns 找到的医生；不存在时返回 undefined
 */
export function findDoctorById(doctorId: number, records: Doctor[]): Doctor | undefined {
  return records.find((doctor) => doctor.id === doctorId);
}

/**
 * 按后端时段开始时间拆分上午和下午号源。
 * @param slots 某一天由后端返回的真实号源
 * @returns 12:00 前的上午号源与 12:00 起的下午号源
 */
export function groupSlotsByHalfDay(slots: AppointmentSlot[]): { morning: AppointmentSlot[]; afternoon: AppointmentSlot[] } {
  return slots.reduce<{ morning: AppointmentSlot[]; afternoon: AppointmentSlot[] }>((result, slot) => {
    // 挂号时段以开始时间为准，12:00 整及之后统一归入下午。
    if (new Date(slot.startTime).getHours() < 12) result.morning.push(slot);
    else result.afternoon.push(slot);
    return result;
  }, { morning: [], afternoon: [] });
}

/** 将本地日期格式化为后端约定的 yyyy-MM-dd。 */
function formatLocalDate(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}
