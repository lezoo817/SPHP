import type { Allergy, AllergyPayload, HealthRecord, MedicalHistory, MedicalHistoryPayload } from '../typings/api';
import { request } from './request';

/** 查询指定就诊人的健康档案。 */
export function getHealthRecord(patientId?: number): Promise<HealthRecord> {
  const query = patientId ? `?patientId=${patientId}` : '';
  return request(`/c/v1/health-record${query}`, { method: 'GET' });
}

/** 新增过敏史。 */
export function createAllergy(payload: AllergyPayload, idempotencyKey: string): Promise<Allergy> {
  return request('/c/v1/health-record/allergies', { method: 'POST', body: payload, headers: { 'X-Idempotency-Key': idempotencyKey } });
}

/** 更新已有过敏史。 */
export function updateAllergy(allergyId: number, payload: AllergyPayload, idempotencyKey: string): Promise<Allergy> {
  return request(`/c/v1/health-record/allergies/${allergyId}`, { method: 'PUT', body: payload, headers: { 'X-Idempotency-Key': idempotencyKey } });
}

/** 新增既往史。 */
export function createMedicalHistory(payload: MedicalHistoryPayload, idempotencyKey: string): Promise<MedicalHistory> {
  return request('/c/v1/health-record/histories', { method: 'POST', body: payload, headers: { 'X-Idempotency-Key': idempotencyKey } });
}

/** 更新已有既往史。 */
export function updateMedicalHistory(historyId: number, payload: MedicalHistoryPayload, idempotencyKey: string): Promise<MedicalHistory> {
  return request(`/c/v1/health-record/histories/${historyId}`, { method: 'PUT', body: payload, headers: { 'X-Idempotency-Key': idempotencyKey } });
}
