import type { FamilyMember, FamilyMemberPayload } from '../typings/api';
import { request } from './request';

/** 查询本人和当前有效家庭成员。 */
export function getFamilyMembers(): Promise<FamilyMember[]> {
  return request('/c/v1/family-members', { method: 'GET' });
}

/** 新增家庭成员并携带防重键。 */
export function createFamilyMember(payload: FamilyMemberPayload, idempotencyKey: string): Promise<FamilyMember> {
  return request('/c/v1/family-members', { method: 'POST', body: payload, headers: { 'X-Idempotency-Key': idempotencyKey } });
}

/** 更新非本人家庭成员资料。 */
export function updateFamilyMember(patientId: number, payload: FamilyMemberPayload, idempotencyKey: string): Promise<FamilyMember> {
  return request(`/c/v1/family-members/${patientId}`, { method: 'PUT', body: payload, headers: { 'X-Idempotency-Key': idempotencyKey } });
}

/** 解绑非本人家庭成员。 */
export function unbindFamilyMember(patientId: number, idempotencyKey: string): Promise<{ patientId: number; unbound: boolean }> {
  return request(`/c/v1/family-members/${patientId}`, { method: 'DELETE', headers: { 'X-Idempotency-Key': idempotencyKey } });
}
