/**
 * 判断接口异常是否为同一账号已有同医生待就诊挂号。
 * @param error 请求层抛出的未知异常
 * @returns 仅当后端返回指定业务码和同医生待就诊挂号文案时返回 true
 */
export function isDuplicateDoctorAppointmentError(error: unknown): boolean {
  // 只有后端明确拒绝重复预约时才锁定当前页面，避免将其他幂等冲突误判为不可预约。
  if (typeof error !== 'object' || error === null) return false;
  const apiError = error as { code?: unknown; message?: unknown };
  return apiError.code === 'A0506'
    && typeof apiError.message === 'string'
    // 兼容已发布的旧文案，且仅接收后端明确的同医生有效挂号冲突提示。
    && (apiError.message.includes('已预约过该医生') || apiError.message.includes('已有该医生待就诊挂号'));
}

/**
 * 判断已支付挂号是否仍可在客户端展示取消入口。
 * 服务端会以 Asia/Shanghai 时区和条件更新作最终校验，客户端仅用于减少无效操作。
 * @param status 挂号订单状态
 * @param startTime 预约开始时间
 * @param now 当前时间戳，便于测试
 * @returns 已支付且尚未到预约开始时间时返回 true
 */
export function canCancelPaidAppointment(status: string, startTime?: string, now = Date.now()): boolean {
  const startAt = startTime ? Date.parse(startTime) : Number.NaN;
  return status === 'PAID' && Number.isFinite(startAt) && startAt > now;
}
