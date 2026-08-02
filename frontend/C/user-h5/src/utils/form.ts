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

/** 校验家庭成员的前端业务约束。 */
export function validateFamilyMember(member: { name: string; relation: string }): string | undefined {
  if (!member.name.trim()) {
    return '请填写成员姓名';
  }
  return member.relation === 'SELF' ? '不能新增或编辑本人资料' : undefined;
}

/** 生成满足后端防重要求的 UUID v4 幂等键。 */
export function createIdempotencyKey(): string {
  return crypto.randomUUID();
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
