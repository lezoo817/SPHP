import { describe, expect, it } from 'vitest';

import {
  createIdempotencyKey,
  getApiErrorMessage,
  validateAccount,
  validateFamilyMember,
  validatePassword,
} from './form';
import { formatAmount, sortHospitals } from './medical';

describe('前端表单与联调规则', () => {
  it('拒绝长度不足的登录账号和密码', () => {
    expect(validateAccount('abc')).toBe('账号长度应为 4 至 32 位');
    expect(validatePassword('1234567')).toBe('密码长度应为 8 至 64 位');
  });

  it('禁止提交本人关系', () => {
    expect(validateFamilyMember({ name: '张三', relation: 'SELF' })).toBe('不能新增或编辑本人资料');
  });

  it('生成符合 UUID 格式的幂等键', () => {
    expect(createIdempotencyKey()).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i);
  });

  it('优先使用后端返回的可读错误信息', () => {
    expect(getApiErrorMessage({ code: 'A0400', message: '账号不能为空', traceId: 'trace-1' })).toBe('账号不能为空');
  });
});

describe('挂号资源展示规则', () => {
  it('按中文拼音排序医院名称', () => {
    expect(sortHospitals([{ name: '上海医院' }, { name: '北京医院' }]).map((item) => item.name)).toEqual(['北京医院', '上海医院']);
  });

  it('将分金额格式化为元', () => {
    expect(formatAmount(1250)).toBe('12.50 元');
  });
});
