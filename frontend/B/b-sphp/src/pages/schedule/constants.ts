/**
 * 排班模块共享常量与安全取值函数（列表/详情/号源池页复用）。
 *
 * 说明：排班元信息可能以「字符串」形式出现（如详情页 query 参数、列表行字段），
 * 直接用 Record<Shift, ...> 索引会因类型不匹配报错，统一经 getShiftConfig / getStatusConfig 收窄。
 */
import dayjs from 'dayjs';

/** 班次类型（取自全局 API 命名空间） */
type Shift = API.Schedule['shift'];
/** 排班状态类型 */
type ScheduleStatus = API.Schedule['status'];

/** 班次展示映射 */
export const SHIFT_MAP: Record<Shift, { text: string; color: string }> = {
  MORNING: { text: '上午', color: 'blue' },
  AFTERNOON: { text: '下午', color: 'geekblue' },
};

/** 班次可排班时间窗口（小时）：号源时段起止均须落在对应窗口内，禁止跨越 12:00-14:00 非上班时段 */
export const SHIFT_WINDOWS: Record<Shift, { start: number; end: number }> = {
  MORNING: { start: 8, end: 12 },
  AFTERNOON: { start: 14, end: 18 },
};

/** 排班状态展示映射 */
export const STATUS_MAP: Record<ScheduleStatus, { text: string; color: string }> = {
  DRAFT: { text: '草稿', color: 'default' },
  PUBLISHED: { text: '已发布', color: 'green' },
  CANCELLED: { text: '已作废', color: 'red' },
};

/** 状态筛选选项 */
export const STATUS_OPTIONS: { label: string; value: ScheduleStatus }[] = [
  { label: '草稿', value: 'DRAFT' },
  { label: '已发布', value: 'PUBLISHED' },
  { label: '已作废', value: 'CANCELLED' },
];

/** 排班已发布时对增删改操作的统一禁用提示 */
export const PUBLISHED_LOCK_TOOLTIP = '排班已发布，不可修改';

/** 安全取班次展示配置；未知班次返回 undefined */
export function getShiftConfig(shift: string): { text: string; color: string } | undefined {
  if (shift === 'MORNING' || shift === 'AFTERNOON') {
    return SHIFT_MAP[shift];
  }
  return undefined;
}

/** 安全取班次时间窗；未知班次返回 undefined（不限制窗口） */
export function getShiftWindow(shift: string): { start: number; end: number } | undefined {
  if (shift === 'MORNING' || shift === 'AFTERNOON') {
    return SHIFT_WINDOWS[shift];
  }
  return undefined;
}

/** 安全取排班状态展示配置；未知状态返回 undefined */
export function getStatusConfig(
  status: string,
): { text: string; color: string } | undefined {
  if (status === 'DRAFT' || status === 'PUBLISHED' || status === 'CANCELLED') {
    return STATUS_MAP[status];
  }
  return undefined;
}

/** 判断排班是否已过期：PUBLISHED 且排班日期 < 今天 */
export function isScheduleExpired(record: { status: string; scheduleDate: string }): boolean {
  if (record.status !== 'PUBLISHED') return false;
  return record.scheduleDate < dayjs().format('YYYY-MM-DD');
}
