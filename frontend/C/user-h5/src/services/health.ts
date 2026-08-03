import type { Allergy, AllergyPayload, FollowUpPlan, HealthRecord, MedicationPlan, MedicationPlanAction, MedicalHistory, MedicalHistoryPayload } from '../typings/api';
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

/** 查询指定就诊人的用药计划。 */
export function getMedicationPlans(patientId?: number): Promise<MedicationPlan[]> {
  return request(`/c/v1/medication-plans${patientId ? `?patientId=${patientId}` : ''}`, { method: 'GET' });
}

/** 按后端状态机更新用药计划。 */
export function updateMedicationPlan(planId: number, action: MedicationPlanAction, idempotencyKey: string): Promise<MedicationPlan> {
  return request(`/c/v1/medication-plans/${planId}`, { method: 'PATCH', body: { action }, headers: { 'X-Idempotency-Key': idempotencyKey } });
}

/** 查询指定就诊人的随访计划。 */
export function getFollowUpPlans(patientId?: number): Promise<FollowUpPlan[]> {
  return request(`/c/v1/follow-ups${patientId ? `?patientId=${patientId}` : ''}`, { method: 'GET' });
}

/** 确认待确认的随访计划，并可指定提醒时间。 */
export function confirmFollowUpPlan(followUpId: number, remindAt: string | undefined, idempotencyKey: string): Promise<FollowUpPlan> {
  return request(`/c/v1/follow-ups/${followUpId}/confirm`, { method: 'POST', body: remindAt ? { remindAt } : {}, headers: { 'X-Idempotency-Key': idempotencyKey } });
}
