import { describe, expect, it } from 'vitest';

import {
  createIdempotencyKey,
  getApiErrorMessage,
  validateAccount,
  validateFamilyMember,
  validatePassword,
} from './form';
import { filterHospitals, formatAmount, sortHospitals } from './medical';
import { resolveSelfPatientId } from '../models/selection';
import { buildDrugOrderListPath } from '../services/pharmacy';
import { matchesDrugOrderTab } from './pharmacy';
import { hasSearchKeyword, resolveInitialDepartment } from './home-search';
import { buildProfileUpdatePayload, resolveProfileIdempotencyKey, validateProfileForm } from './profile';
import { resolveMinePatientId } from '../models/mine-patient';
import { isSessionTokenExpired, type SessionState } from '../models/session';

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

describe('就诊人默认选择', () => {
  it('优先选择本人而非全局家属选择', () => {
    expect(resolveSelfPatientId([{ patientId: 2, relation: 'CHILD' }, { patientId: 1, relation: 'SELF' }])).toBe(1);
  });
});

describe('挂号资源展示规则', () => {
  it('按中文拼音排序医院名称', () => {
    expect(sortHospitals([{ name: '上海医院' }, { name: '北京医院' }]).map((item) => item.name)).toEqual(['北京医院', '上海医院']);
  });

  it('将分金额格式化为元', () => {
    expect(formatAmount(1250)).toBe('12.50 元');
  });

  it('按关键词筛选医院名称', () => {
    expect(filterHospitals([{ name: '省人民医院' }, { name: '市中医院' }], '人民')).toEqual([{ name: '省人民医院' }]);
  });
});

describe('购药订单展示规则', () => {
  it('运输中同时包含已发货和运输中状态', () => {
    expect(matchesDrugOrderTab({ id: 1, orderName: '阿莫西林', pharmacyName: '健康药房', status: 'PAID', logisticsStatus: 'SHIPPED', amountCent: 100 }, 'TRANSIT')).toBe(true);
    expect(matchesDrugOrderTab({ id: 2, orderName: '维生素', pharmacyName: '健康药房', status: 'PAID', logisticsStatus: 'TO_RECEIVE', amountCent: 100 }, 'TRANSIT')).toBe(false);
  });

  it('订单名称关键词经过编码并传递给列表接口', () => {
    expect(buildDrugOrderListPath({ patientId: 20001, keyword: '阿莫 西林', pageSize: 100 })).toContain('keyword=%E9%98%BF%E8%8E%AB+%E8%A5%BF%E6%9E%97');
  });
});

describe('首页科室与搜索规则', () => {
  it('默认选择当前医院的第一个科室', () => {
    expect(resolveInitialDepartment([{ id: 2, name: '外科' }, { id: 1, name: '内科' }])).toEqual({ id: 2, name: '外科' });
  });

  it('空白关键词不允许发起搜索', () => {
    expect(hasSearchKeyword('   ')).toBe(false);
    expect(hasSearchKeyword('心内科')).toBe(true);
  });
});

describe('个人资料更新规则', () => {
  const values = { name: ' 张三 ', gender: 'MALE' as const, birthday: '2000-01-01', phone: '', emergencyContact: '' };

  it('校验姓名和手机号格式', () => {
    expect(validateProfileForm({ ...values, name: ' ' })).toBe('请填写姓名');
    expect(validateProfileForm({ ...values, phone: '123' })).toBe('手机号格式不正确');
  });

  it('不提交空白的敏感资料字段', () => {
    expect(buildProfileUpdatePayload(values)).toEqual({ name: '张三', gender: 'MALE', birthday: '2000-01-01' });
  });

  it('网络重试复用首次生成的幂等键', () => {
    const first = resolveProfileIdempotencyKey();
    expect(resolveProfileIdempotencyKey(first)).toBe(first);
  });
});

describe('我的页面就诊人选择规则', () => {
  const members = [{ patientId: 1, relation: 'SELF', isDefault: true }, { patientId: 2, relation: 'PARENT' }];

  it('默认优先选择本人', () => {
    expect(resolveMinePatientId(members, undefined)).toBe(1);
  });

  it('保留仍有效的已选家属', () => {
    expect(resolveMinePatientId(members, 2)).toBe(2);
  });

  it('家属失效后回退本人', () => {
    expect(resolveMinePatientId(members, 99)).toBe(1);
  });
});

describe('登录会话有效期规则', () => {
  const session: SessionState = { accessToken: 'access', refreshToken: 'refresh', expiresIn: 60, user: { id: 1, account: 'patient' }, loginAt: '2026-08-03T00:00:00.000Z', accessTokenIssuedAt: '2026-08-03T00:00:00.000Z' };

  it('有效令牌在过期时间前可继续访问', () => {
    expect(isSessionTokenExpired(session, Date.parse('2026-08-03T00:00:59.000Z'))).toBe(false);
  });

  it('令牌缺失或超过 expiresIn 后视为失效', () => {
    expect(isSessionTokenExpired(null)).toBe(true);
    expect(isSessionTokenExpired(session, Date.parse('2026-08-03T00:01:00.000Z'))).toBe(true);
  });
});
