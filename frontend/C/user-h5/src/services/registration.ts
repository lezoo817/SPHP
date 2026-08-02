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
/** 查询指定就诊人的挂号订单。 */
export function getAppointments(patientId?: number, status?: string): Promise<PageData<Appointment>> { return request(`/c/v1/appointments?patientId=${patientId || ''}&status=${status || ''}&pageNo=1&pageSize=20`, { method: 'GET' }); }
/** 查询挂号订单详情。 */
export function getAppointment(appointmentId: number): Promise<AppointmentDetail> { return request(`/c/v1/appointments/${appointmentId}`, { method: 'GET' }); }
/** 取消未支付挂号订单。 */
export function cancelAppointment(appointmentId: number, key: string): Promise<{ status: string }> { return request(`/c/v1/appointments/${appointmentId}/cancel`, { method: 'POST', headers: { 'X-Idempotency-Key': key } }); }
/** 创建无余量时段的候补登记。 */
export function createWaitlist(payload: { patientId?: number; slotId: number }, key: string): Promise<{ queueNo: number }> { return request('/c/v1/waitlists', { method: 'POST', body: payload, headers: { 'X-Idempotency-Key': key } }); }
/** 查询挂号支付单。 */
export function getPayment(paymentId: number): Promise<{ id: number; amountCent: number; status: string; expireAt?: string }> { return request(`/c/v1/payments/${paymentId}`, { method: 'GET' }); }
/** 使用登录密码完成模拟支付。 */
export function simulatePayment(paymentId: number, loginPassword: string, key: string): Promise<{ status: string; paidAt: string }> { return request(`/c/v1/payments/${paymentId}/simulate-pay`, { method: 'POST', body: { loginPassword }, headers: { 'X-Idempotency-Key': key } }); }
