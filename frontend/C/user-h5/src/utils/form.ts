import type { FamilyMemberPayload } from '../typings/api';

/** 校验登录或注册账号长度。 */
export function validateAccount(account: string): string | undefined {
  return account.trim().length >= 4 && account.trim().length <= 32
    ? undefined
    : '账号长度应为 4 至 32 位';
}

/** 校验登录或注册密码长度。 */
export function validatePassword(password: string): string | undefined {
  return password.length >= 8 && password.length <= 64
    ? undefined
    : '密码长度应为 8 至 64 位';
}

/**
 * 校验家庭成员资料，新增时身份证号必须填写，编辑时留空表示不修改。
 * @param member 家属可提交资料
 * @param requireIdCardNo 是否按新增规则要求身份证号
 * @returns 校验失败文案；合法时返回 undefined
 */
export function validateFamilyMember(
  member: Pick<FamilyMemberPayload, 'name' | 'idCardNo'> & { relation: string },
  requireIdCardNo = false,
): string | undefined {
  if (!member.name.trim()) {
    return '请填写成员姓名';
  }
  if (member.relation === 'SELF') {
    return '不能新增或编辑本人资料';
  }
  // 编辑留空时不提交身份证号，避免将列表中的脱敏值写回服务端。
  const idCardNo = member.idCardNo?.replace(/\s/g, '').toUpperCase() || '';
  if (requireIdCardNo && !idCardNo) {
    return '请填写身份证号';
  }
  return idCardNo && !/^(\d{15}|\d{17}[0-9X])$/.test(idCardNo) ? '身份证号格式不正确' : undefined;
}

/**
 * 生成满足后端防重要求的 UUID v4 幂等键。
 *
 * @returns UUID v4 格式的幂等键
 */
export function createIdempotencyKey(): string {
  const cryptoApi = typeof window !== 'undefined' ? window.crypto : undefined;
  if (cryptoApi && typeof cryptoApi.randomUUID === 'function') {
    return cryptoApi.randomUUID();
  }

  // 旧版移动浏览器可能没有 randomUUID，但通常仍支持 getRandomValues。
  const bytes = new Uint8Array(16);
  if (cryptoApi && typeof cryptoApi.getRandomValues === 'function') {
    cryptoApi.getRandomValues(bytes);
  } else {
    // 极旧环境的最后回退，幂等键仍保持唯一性和 UUID v4 格式。
    for (let index = 0; index < bytes.length; index += 1) {
      bytes[index] = Math.floor(Math.random() * 256);
    }
  }
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, (value) => value.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

/** 获取后端返回中可直接展示的错误提示。 */
export function getApiErrorMessage(error: unknown): string {
  // catch 块中的异常为 unknown，先安全读取后端错误的 message 字段。
  if (typeof error === 'object' && error !== null && 'message' in error && typeof error.message === 'string') {
    return error.message;
  }
  return '请求未完成，请稍后重试';
}

/** 将关系编码转换为表单显示名称。 */
export function getRelationLabel(relation: string): string {
  return ({ SPOUSE: '配偶', PARENT: '父母', CHILD: '子女', OTHER: '其他', SELF: '本人' } as Record<string, string>)[relation] || relation;
}
