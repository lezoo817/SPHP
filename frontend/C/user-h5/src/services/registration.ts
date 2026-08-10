import type { Appointment, AppointmentDetail, AppointmentSlot, Department, Doctor, Hospital, PageData } from '../typings/api';
import { request } from './request';

/** 查询当前 C 端可选择的医院。 */
export function getHospitals(): Promise<Hospital[]> { return request('/c/v1/hospitals', { method: 'GET' }); }
/** 查询指定医院的科室。 */
export function getDepartments(hospitalId: number, keyword?: string): Promise<Department[]> { return request(`/c/v1/departments?hospitalId=${hospitalId}${keyword ? `&keyword=${encodeURIComponent(keyword)}` : ''}`, { method: 'GET' }); }
/** 查询指定科室的医生。 */
export function getDoctors(hospitalId: number, departmentId: number, date: string): Promise<PageData<Doctor>> { return request(`/c/v1/doctors?hospitalId=${hospitalId}&departmentId=${departmentId}&date=${date}&pageNo=1&pageSize=100`, { method: 'GET' }); }
/** 查询医生在指定日期的时段。 */
export function getSlots(doctorId: number, hospitalId: number, date: string): Promise<AppointmentSlot[]> { return request(`/c/v1/doctors/${doctorId}/slots?hospitalId=${hospitalId}&date=${date}`, { method: 'GET' }); }
/** 创建挂号订单。 */
export function createAppointment(payload: { patientId?: number; hospitalId: number; slotId: number }, key: string): Promise<{ appointmentId: number; paymentId: number; amountCent: number; expireAt: string }> { return request('/c/v1/appointments', { method: 'POST', body: payload, headers: { 'X-Idempotency-Key': key } }); }
/**
 * 构建挂号订单列表请求路径。
 * @param patientId 当前就诊人 ID，可不传以查询本人订单
 * @param status 挂号订单状态，可不传以查询全部状态
 * @param pageSize 每页记录数量，最大为 100
 * @param pageNo 查询页码，从 1 开始
 * @returns 不包含空状态参数的挂号订单列表路径
 */
export function buildAppointmentsPath(patientId?: number, status?: string, pageSize = 20, pageNo = 1): string {
  const params = new URLSearchParams({ pageNo: String(pageNo), pageSize: String(pageSize) });
  if (patientId !== undefined) params.set('patientId', String(patientId));
  // 后端会校验枚举值，未筛选时不能发送空字符串 status=。
  if (status?.trim()) params.set('status', status.trim());
  return `/c/v1/appointments?${params.toString()}`;
}

/**
 * 查询指定就诊人的挂号订单。
 * @param patientId 当前就诊人 ID
 * @param status 可选的挂号订单状态筛选
 * @param pageSize 每位就诊人需读取的订单数量，最大为 100
 * @param pageNo 查询页码，加载更多时递增
 * @returns 分页挂号订单数据
 */
export function getAppointments(patientId?: number, status?: string, pageSize = 20, pageNo = 1): Promise<PageData<Appointment>> {
  return request(buildAppointmentsPath(patientId, status, pageSize, pageNo), { method: 'GET' });
}
/**
 * 构建医生重复预约状态查询路径。
 * @param doctorId 医生 ID
 * @param patientId 当前就诊人 ID，未传时由服务端使用本人
 * @returns 当前就诊人预约状态接口路径
 */
export function buildDoctorBookingStatusPath(doctorId: number, patientId?: number): string {
  const params = new URLSearchParams({ doctorId: String(doctorId) });
  if (patientId !== undefined) params.set('patientId', String(patientId));
  return `/c/v1/appointments/doctor-booking-status?${params.toString()}`;
}
/**
 * 查询当前就诊人是否已有指定医生的有效待就诊挂号。
 * @param doctorId 医生 ID
 * @param patientId 当前就诊人 ID，未传时由服务端使用本人
 * @returns 当前就诊人维度的有效待就诊挂号状态
 */
export function getDoctorBookingStatus(doctorId: number, patientId?: number): Promise<{ doctorId: number; booked: boolean }> {
  return request(buildDoctorBookingStatusPath(doctorId, patientId), { method: 'GET' });
}
/** 查询挂号订单详情。 */
export function getAppointment(appointmentId: number): Promise<AppointmentDetail> { return request(`/c/v1/appointments/${appointmentId}`, { method: 'GET' }); }
/**
 * 取消挂号订单；已支付订单必须传入登录密码，未支付订单不传请求体以兼容原有接口。
 * @param appointmentId 挂号订单 ID
 * @param key 幂等键
 * @param loginPassword 已支付取消时的当前登录密码
 * @returns 服务端最终取消状态
 */
export function cancelAppointment(appointmentId: number, key: string, loginPassword?: string): Promise<{ status: string }> { return request(`/c/v1/appointments/${appointmentId}/cancel`, { method: 'POST', body: loginPassword === undefined ? undefined : { loginPassword }, headers: { 'X-Idempotency-Key': key } }); }
/** 创建无余量时段的候补登记。 */
export function createWaitlist(payload: { patientId?: number; slotId: number }, key: string): Promise<{ queueNo: number }> { return request('/c/v1/waitlists', { method: 'POST', body: payload, headers: { 'X-Idempotency-Key': key } }); }
/** 查询挂号支付单。 */
export function getPayment(paymentId: number): Promise<{ id: number; amountCent: number; status: string; expireAt?: string }> { return request(`/c/v1/payments/${paymentId}`, { method: 'GET' }); }
/** 使用登录密码完成模拟支付。 */
export function simulatePayment(paymentId: number, loginPassword: string, key: string): Promise<{ status: string; paidAt: string }> { return request(`/c/v1/payments/${paymentId}/simulate-pay`, { method: 'POST', body: { loginPassword }, headers: { 'X-Idempotency-Key': key } }); }
