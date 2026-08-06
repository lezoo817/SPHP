import type { ProfileUpdatePayload } from '../typings/api';
import { createIdempotencyKey } from './form';

/** 个人资料编辑页维护的表单字段。 */
export interface ProfileFormValues {
  name: string;
  gender: '' | 'MALE' | 'FEMALE' | 'UNKNOWN';
  birthday: string;
  phone: string;
  idCardNo: string;
  emergencyContact: string;
}

/**
 * 规范化身份证号输入，防止空白字符和小写校验位进入更新请求。
 * @param idCardNo 用户输入的身份证号
 * @returns 去除空白并转换为大写后的身份证号
 */
export function normalizeProfileIdCardNo(idCardNo: string): string {
  return idCardNo.replace(/\s/g, '').toUpperCase();
}

/**
 * 校验个人资料更新请求的前端约束。
 * @param values 用户输入的资料字段
 * @returns 校验失败文案；合法时返回 undefined
 */
export function validateProfileForm(values: ProfileFormValues): string | undefined {
  const name = values.name.trim();
  if (!name) return '请填写姓名';
  if (name.length > 64) return '姓名长度不能超过64位';
  if (values.gender && !['MALE', 'FEMALE', 'UNKNOWN'].includes(values.gender)) return '性别选择不合法';
  if (values.birthday && values.birthday > new Date().toISOString().slice(0, 10)) return '出生日期不能晚于当天';
  if (values.phone.trim() && !/^1[3-9]\d{9}$/.test(values.phone.trim())) return '手机号格式不正确';
  if (values.idCardNo.trim() && !/^(\d{15}|\d{17}[0-9X])$/.test(normalizeProfileIdCardNo(values.idCardNo))) return '身份证号格式不正确';
  if (values.emergencyContact.trim().length > 256) return '紧急联系人长度不能超过256位';
  return undefined;
}

/**
 * 将表单转换为局部更新请求，避免将服务端脱敏值写回数据库。
 * @param values 用户编辑的资料字段
 * @returns 可直接传给更新接口的请求对象
 */
export function buildProfileUpdatePayload(values: ProfileFormValues): ProfileUpdatePayload {
  const phone = values.phone.trim();
  const idCardNo = normalizeProfileIdCardNo(values.idCardNo);
  const emergencyContact = values.emergencyContact.trim();
  return {
    name: values.name.trim(),
    ...(values.gender ? { gender: values.gender } : {}),
    ...(values.birthday ? { birthday: values.birthday } : {}),
    // 敏感字段为空表示不修改，不能把脱敏展示值重新提交。
    ...(phone ? { phone } : {}),
    ...(idCardNo ? { idCardNo } : {}),
    ...(emergencyContact ? { emergencyContact } : {}),
  };
}

/**
 * 获取资料保存操作所需的幂等键。
 * @param existingKey 已存在的重试幂等键
 * @returns 原键或首次生成的新 UUID
 */
export function resolveProfileIdempotencyKey(existingKey?: string): string {
  return existingKey || createIdempotencyKey();
}
