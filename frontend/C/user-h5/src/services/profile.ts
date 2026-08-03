import type { Profile, ProfileUpdatePayload, ProfileUpdateResult } from '../typings/api';
import { request } from './request';

/**
 * 查询当前登录账号的本人资料。
 * @returns 已由服务端脱敏的本人资料
 */
export function getProfile(): Promise<Profile> {
  return request('/c/v1/profile', { method: 'GET' });
}

/**
 * 更新当前登录账号的本人资料。
 * @param payload 本人资料更新字段
 * @param idempotencyKey 同一次提交及网络重试复用的幂等键
 * @returns 更新后的最小资料摘要
 */
export function updateProfile(payload: ProfileUpdatePayload, idempotencyKey: string): Promise<ProfileUpdateResult> {
  return request('/c/v1/profile', { method: 'PUT', body: payload, headers: { 'X-Idempotency-Key': idempotencyKey } });
}
