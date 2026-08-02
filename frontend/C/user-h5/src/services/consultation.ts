import type { Consultation, ConsultationDetail, PageData, Prescription, PrescriptionDetail } from '../typings/api';
import { request } from './request';

/** 保存或提交预问诊信息。 */
export function savePreConsultation(payload: { patientId?: number; appointmentId: number; chiefComplaint: string; historyOfPresentIllness?: string; submit: boolean }, key: string): Promise<{ consultationId: number; status: string }> { return request('/c/v1/consultations/pre-consultations', { method: 'POST', body: payload, headers: { 'X-Idempotency-Key': key } }); }
/** 查询当前就诊人的问诊记录。 */
export function getConsultations(patientId?: number): Promise<PageData<Consultation>> { return request(`/c/v1/consultations?patientId=${patientId || ''}&pageNo=1&pageSize=20`, { method: 'GET' }); }
/** 查询问诊详情和文字消息。 */
export function getConsultation(id: number): Promise<ConsultationDetail> { return request(`/c/v1/consultations/${id}`, { method: 'GET' }); }
/** 发送患者文字消息。 */
export function sendConsultationMessage(id: number, content: string, key: string): Promise<{ messageId: number; content: string; createdAt: string }> { return request(`/c/v1/consultations/${id}/messages`, { method: 'POST', body: { content }, headers: { 'X-Idempotency-Key': key } }); }
/** 查询已批准处方。 */
export function getPrescriptions(patientId?: number): Promise<PageData<Prescription>> { return request(`/c/v1/prescriptions?patientId=${patientId || ''}&pageNo=1&pageSize=20`, { method: 'GET' }); }
/** 查询已批准处方详情。 */
export function getPrescription(id: number): Promise<PrescriptionDetail> { return request(`/c/v1/prescriptions/${id}`, { method: 'GET' }); }
