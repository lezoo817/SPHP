/**
 * 患者详情页状态映射常量。
 *
 * 各类状态（性别 / 处方 / 就诊 / 用药 / 随访 / 过敏严重程度）统一在此定义，
 * 供列配置与子组件渲染时复用，避免各文件重复声明。
 */

/** 性别文本 */
export const genderTextMap: Record<string, string> = {
  MALE: '男',
  FEMALE: '女',
  UNKNOWN: '未知',
};

/** 性别标签颜色 */
export const genderColorMap: Record<string, string> = {
  MALE: 'blue',
  FEMALE: 'magenta',
  UNKNOWN: 'default',
};

/** 处方状态映射 */
export const prescriptionStatusMap: Record<string, { text: string; color: string }> = {
  DRAFT: { text: '草稿', color: 'default' },
  SUBMITTED: { text: '待审核', color: 'processing' },
  APPROVED: { text: '已通过', color: 'success' },
  REJECTED: { text: '已驳回', color: 'error' },
  CANCELLED: { text: '已作废', color: 'warning' },
};

/** 就诊状态映射 */
export const visitStatusMap: Record<string, { text: string; color: string }> = {
  PENDING: { text: '待接诊', color: 'processing' },
  IN_PROGRESS: { text: '接诊中', color: 'warning' },
  COMPLETED: { text: '已完成', color: 'success' },
  NO_SHOW: { text: '未到诊', color: 'error' },
};

/** 用药状态映射 */
export const medicationStatusMap: Record<string, { text: string; color: string }> = {
  ACTIVE: { text: '进行中', color: 'success' },
  PAUSED: { text: '已暂停', color: 'warning' },
  COMPLETED: { text: '已完成', color: 'default' },
};

/** 随访状态映射 */
export const followUpStatusMap: Record<string, { text: string; color: string }> = {
  PENDING_CONFIRM: { text: '待确认', color: 'processing' },
  CONFIRMED: { text: '已确认', color: 'warning' },
  COMPLETED: { text: '已完成', color: 'success' },
  CANCELLED: { text: '已取消', color: 'error' },
};

/** 过敏严重程度颜色映射 */
export const severityColorMap: Record<string, string> = {
  MILD: 'green',
  MODERATE: 'orange',
  SEVERE: 'red',
};
