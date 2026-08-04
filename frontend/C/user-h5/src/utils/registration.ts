/**
 * 判断接口异常是否为同一账号重复预约同一医生。
 * @param error 请求层抛出的未知异常
 * @returns 仅当后端返回指定业务码和重复预约文案时返回 true
 */
export function isDuplicateDoctorAppointmentError(error: unknown): boolean {
  // 只有后端明确拒绝重复预约时才锁定当前页面，避免将其他幂等冲突误判为不可预约。
  if (typeof error !== 'object' || error === null) return false;
  const apiError = error as { code?: unknown; message?: unknown };
  return apiError.code === 'A0506'
    && typeof apiError.message === 'string'
    && apiError.message.includes('已预约过该医生');
}
