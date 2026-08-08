import type { Consultation, ConsultationDetail, PageData, Prescription, PrescriptionDetail } from '../typings/api';
import { request } from './request';

/** 已批准处方列表的分页查询参数。 */
export interface PrescriptionListParams {
  /** 可选的当前就诊人编号。 */
  patientId?: number;
  /** 页号，默认第一页。 */
  pageNo?: number;
  /** 每页条数，默认二十条。 */
  pageSize?: number;
}

/**
 * 向指定医生提交无挂号在线预问诊。
 * @param payload 医生和预问诊内容
 * @param key 幂等键
 * @returns 新建问诊记录
 */
export function savePreConsultation(payload: { doctorId: number; chiefComplaint: string; historyOfPresentIllness?: string }, key: string): Promise<{ consultationId: number; status: string }> { return request('/c/v1/consultations/pre-consultations', { method: 'POST', body: payload, headers: { 'X-Idempotency-Key': key } }); }
/** 查询当前就诊人的问诊记录。 */
export function getConsultations(patientId?: number): Promise<PageData<Consultation>> { return request(`/c/v1/consultations?patientId=${patientId || ''}&pageNo=1&pageSize=20`, { method: 'GET' }); }
/** 查询问诊详情和文字消息。 */
export function getConsultation(id: number): Promise<ConsultationDetail> { return request(`/c/v1/consultations/${id}`, { method: 'GET' }); }
/** 发送患者文字消息。 */
export function sendConsultationMessage(id: number, content: string, key: string): Promise<{ messageId: number; content: string; createdAt: string }> { return request(`/c/v1/consultations/${id}/messages`, { method: 'POST', body: { content }, headers: { 'X-Idempotency-Key': key } }); }
/**
 * 构建已批准处方列表请求路径。
 * @param params 就诊人与分页参数
 * @returns 不包含空查询参数的接口路径
 */
export function buildPrescriptionsPath({ patientId, pageNo = 1, pageSize = 20 }: PrescriptionListParams = {}): string {
  const search = new URLSearchParams({ pageNo: String(pageNo), pageSize: String(pageSize) });
  // 未选择就诊人时由后端按当前账号处理，避免提交空字符串导致参数校验异常。
  if (patientId && patientId > 0) search.set('patientId', String(patientId));
  return `/c/v1/prescriptions?${search.toString()}`;
}

/**
 * 查询已批准处方。
 * @param params 就诊人与分页参数
 * @returns 已批准处方分页数据
 */
export function getPrescriptions(params: PrescriptionListParams = {}): Promise<PageData<Prescription>> { return request(buildPrescriptionsPath(params), { method: 'GET' }); }
/** 查询已批准处方详情。 */
export function getPrescription(id: number): Promise<PrescriptionDetail> { return request(`/c/v1/prescriptions/${id}`, { method: 'GET' }); }
