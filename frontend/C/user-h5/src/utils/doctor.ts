import type { AppointmentSlot, Doctor } from '../typings/api';

/** 医生号源页的一天展示信息。 */
export interface DoctorScheduleDate { value: string; day: string; weekday: string; }

/** 医生某日半天号源的汇总结果。 */
export interface HalfDaySlotSummary {
  /** 该半天的真实排班数量。 */
  slotCount: number;
  /** 后端返回的可预约余量总和。 */
  availableCount: number;
  /** 点击挂号时优先使用的最早可预约号源；无余量时回退到首个号源候补。 */
  targetSlot?: AppointmentSlot;
}

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

/**
 * 汇总一个半天的真实余号，并选出可用于创建挂号或候补登记的号源。
 * @param slots 同一半天内按后端时间顺序返回的号源
 * @returns 供课程表单元格展示和点击操作使用的汇总数据
 */
export function summarizeHalfDaySlots(slots: AppointmentSlot[]): HalfDaySlotSummary {
  // 优先选择仍有余量的最早时段，避免在用户点击“挂号”时创建候补记录。
  const targetSlot = slots.find((slot) => slot.availableCount > 0) || slots[0];
  return {
    slotCount: slots.length,
    availableCount: slots.reduce((total, slot) => total + Math.max(slot.availableCount, 0), 0),
    targetSlot,
  };
}

/** 将本地日期格式化为后端约定的 yyyy-MM-dd。 */
function formatLocalDate(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}
