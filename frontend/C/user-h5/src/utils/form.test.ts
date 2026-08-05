import { describe, expect, it } from 'vitest';

import {
  createIdempotencyKey,
  getApiErrorMessage,
  validateAccount,
  validateFamilyMember,
  validatePassword,
} from './form';
import { filterHospitals, formatAmount, getAppointmentStatusText, sortHospitals } from './medical';
import { resolveSelfPatientId } from '../models/selection';
import { buildDrugOrderListPath } from '../services/pharmacy';
import { buildPharmacyInventoryPath, buildPharmacyPrescriptionPath, matchesDrugOrderTab, resolvePharmacyPatientId } from './pharmacy';
import { hasSearchKeyword, matchesDepartmentKeyword, resolveInitialDepartment } from './home-search';
import { buildProfileUpdatePayload, resolveProfileIdempotencyKey, validateProfileForm } from './profile';
import { resolveMinePatientId } from '../models/mine-patient';
import { isSessionTokenExpired, type SessionState } from '../models/session';
import { buildDoctorPagePath, findDoctorById, getDoctorScheduleDates } from './doctor';
import { groupSlotsByHalfDay, summarizeHalfDaySlots } from './doctor';
import { buildAppointmentsPath } from '../services/registration';
import { buildNotificationsPath } from '../services/notification';
import { buildHealthTodos, canConfirmFollowUp, findLatestWaitlistPromotionNotification, getMedicationPlanActions, getNotificationTypeText, resolveNotificationReadKey } from './health-notification';
import { buildDeliveryAddressPath } from '../services/delivery-address';
import { buildDeliveryAddressPayload, getDeliveryCities, getDeliveryProvinces, resolveDeliveryIdempotencyKey, validateDeliveryAddress } from './delivery-address';
import { getAssistantTabs, getCurrentFlowAction } from './assistant';
import { buildMedicalRecordDetailPath, buildMedicalRecordListPath } from '../services/medical-record';
import { buildLegacyReportRedirectPath, createMedicalRecordDisplayNumber, filterMedicalRecordsByDate, getRecentMedicalRecordRange, mergeMedicalRecordPages } from './medical-record';
import { isDuplicateDoctorAppointmentError } from './registration';
import { buildDoctorBookingStatusPath } from '../services/registration';

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

describe('重复预约联调规则', () => {
  it('仅识别后端明确返回的重复预约冲突', () => {
    expect(isDuplicateDoctorAppointmentError({ code: 'A0506', message: '已预约过该医生，不可重复预约' })).toBe(true);
    expect(isDuplicateDoctorAppointmentError({ code: 'A0506', message: '幂等键冲突' })).toBe(false);
    expect(isDuplicateDoctorAppointmentError({ code: 'A0400', message: '已预约过该医生，不可重复预约' })).toBe(false);
    expect(isDuplicateDoctorAppointmentError(new Error('已预约过该医生，不可重复预约'))).toBe(false);
  });

  it('医生主页按医生 ID 查询账号维度的预约状态', () => {
    expect(buildDoctorBookingStatusPath(401)).toBe('/c/v1/appointments/doctor-booking-status?doctorId=401');
  });
});

describe('就诊人默认选择', () => {
  it('优先选择本人而非全局家属选择', () => {
    expect(resolveSelfPatientId([{ patientId: 2, relation: 'CHILD' }, { patientId: 1, relation: 'SELF' }])).toBe(1);
  });
});

describe('购药处方跳转规则', () => {
  it('购药处方详情和库存页始终透传当前本地就诊人', () => {
    expect(buildPharmacyPrescriptionPath(13001, 20001)).toBe('/pharmacy/prescription/13001?patientId=20001');
    expect(buildPharmacyInventoryPath(13001, 20001)).toBe('/pharmacy/prescription/13001/inventory?patientId=20001');
  });

  it('缺失或非法就诊人参数时不解析为库存请求患者', () => {
    expect(resolvePharmacyPatientId('20001')).toBe(20001);
    expect(resolvePharmacyPatientId(null)).toBeUndefined();
    expect(resolvePharmacyPatientId('0')).toBeUndefined();
    expect(resolvePharmacyPatientId('patient')).toBeUndefined();
  });
});

describe('就诊助手展示规则', () => {
  it('仅展示挂号记录和处方两个分类', () => {
    expect(getAssistantTabs).toEqual(['挂号记录', '处方']);
  });

  it('仅未支付订单可进入支付，已支付订单保持等待就诊', () => {
    expect(getCurrentFlowAction('UNPAID')).toBe('PAY');
    expect(getCurrentFlowAction('PAID')).toBe('WAITING');
  });
});

describe('病历报告查询规则', () => {
  it('病历请求始终携带当前就诊人和最大分页大小', () => {
    expect(buildMedicalRecordListPath({ patientId: 20001 })).toBe('/c/v1/medical-records?patientId=20001&pageNo=1&pageSize=100');
    expect(buildMedicalRecordDetailPath(7001)).toBe('/c/v1/medical-records/7001');
  });

  it('最近 30、90、180 天均生成包含当天的日期范围', () => {
    const now = new Date('2026-08-04T10:00:00+08:00');
    expect(getRecentMedicalRecordRange(30, now)).toEqual({ startDate: '2026-07-06', endDate: '2026-08-04' });
    expect(getRecentMedicalRecordRange(90, now).startDate).toBe('2026-05-07');
    expect(getRecentMedicalRecordRange(180, now).startDate).toBe('2026-02-06');
  });

  it('按完成日期筛选并保持病历倒序，不匹配范围不返回结果', () => {
    const medicalRecords = [
      { id: 1, patientId: 1, doctorName: '张医生', departmentName: '内科', completedAt: '2026-07-07T08:00:00+08:00', updatedAt: '2026-07-07T08:00:00+08:00' },
      { id: 2, patientId: 1, doctorName: '李医生', departmentName: '外科', completedAt: '2026-08-03T08:00:00+08:00', updatedAt: '2026-08-03T08:00:00+08:00' },
    ];
    expect(filterMedicalRecordsByDate(medicalRecords, { startDate: '2026-08-01', endDate: '2026-08-04' }).map((item) => item.id)).toEqual([2]);
    expect(filterMedicalRecordsByDate(medicalRecords, { startDate: '2026-08-05', endDate: '2026-08-04' })).toEqual([]);
  });

  it('加载更多时按病历 ID 去重并使用新页的最新记录', () => {
    const base = { patientId: 1, doctorName: '张医生', departmentName: '内科', completedAt: '2026-08-03T08:00:00+08:00' };
    const merged = mergeMedicalRecordPages([{ id: 1, ...base, updatedAt: '2026-08-03T08:00:00+08:00' }], [{ id: 1, ...base, updatedAt: '2026-08-03T09:00:00+08:00' }, { id: 2, ...base, updatedAt: '2026-08-02T08:00:00+08:00' }]);
    expect(merged).toHaveLength(2);
    expect(merged.find((item) => item.id === 1)?.updatedAt).toBe('2026-08-03T09:00:00+08:00');
  });

  it('报告编号由完成时间戳和固定六位随机尾号组成', () => {
    expect(createMedicalRecordDisplayNumber('2026-08-04T10:00:00+08:00', 123)).toBe('1785808800000000123');
    expect(createMedicalRecordDisplayNumber(undefined, 123)).toBe('暂未提供');
  });

  it('旧报告链接跳转时保留查询参数', () => {
    expect(buildLegacyReportRedirectPath('7001', '?source=home&patientId=20001')).toBe('/medical-records/7001?source=home&patientId=20001');
    expect(buildLegacyReportRedirectPath(undefined, '?source=mine')).toBe('/medical-records?source=mine');
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

  it('未指定订单状态时不传递空状态参数', () => {
    expect(buildAppointmentsPath(1)).toBe('/c/v1/appointments?pageNo=1&pageSize=20&patientId=1');
    expect(buildAppointmentsPath(1, 'UNPAID')).toContain('status=UNPAID');
  });

  it('将挂号订单状态转换为患者可理解的中文文案', () => {
    expect(getAppointmentStatusText('PAID')).toBe('支付完成');
    expect(getAppointmentStatusText('CANCELLED')).toBe('支付取消');
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

  it('科室位置可作为科室搜索关键词', () => {
    expect(matchesDepartmentKeyword({ id: 1, name: '呼吸内科', location: '门诊楼3层A区' }, '3层')).toBe(true);
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

describe('医生个人挂号页规则', () => {
  it('生成连续七天的真实号源日期', () => {
    expect(getDoctorScheduleDates(new Date(2026, 7, 3)).map((item) => item.value)).toEqual(['2026-08-03', '2026-08-04', '2026-08-05', '2026-08-06', '2026-08-07', '2026-08-08', '2026-08-09']);
  });

  it('携带科室上下文进入医生个人页', () => {
    expect(buildDoctorPagePath(11, 22)).toBe('/assistant/doctor/11?departmentId=22');
  });

  it('深链接回退查询时按医生 ID 定位资料', () => {
    expect(findDoctorById(2, [{ id: 1, name: '甲', registrationFeeCent: 100, availableCount: 1 }, { id: 2, name: '乙', registrationFeeCent: 100, availableCount: 0, departmentId: 3 }])?.departmentId).toBe(3);
  });

  it('按后端时段开始时间将号源划分为上午和下午', () => {
    const slots = [
      { slotId: 1, startTime: '2026-08-04T09:30:00+08:00', endTime: '2026-08-04T10:00:00+08:00', feeCent: 3000, availableCount: 5 },
      { slotId: 2, startTime: '2026-08-04T12:00:00+08:00', endTime: '2026-08-04T12:30:00+08:00', feeCent: 3000, availableCount: 4 },
    ];
    expect(groupSlotsByHalfDay(slots).morning.map((item) => item.slotId)).toEqual([1]);
    expect(groupSlotsByHalfDay(slots).afternoon.map((item) => item.slotId)).toEqual([2]);
  });

  it('将同一半天的多段号源汇总余量并优先选择可挂号时段', () => {
    const summary = summarizeHalfDaySlots([
      { slotId: 1, startTime: '2026-08-04T09:30:00+08:00', endTime: '2026-08-04T10:00:00+08:00', feeCent: 3000, availableCount: 0 },
      { slotId: 2, startTime: '2026-08-04T10:00:00+08:00', endTime: '2026-08-04T10:30:00+08:00', feeCent: 3000, availableCount: 5 },
    ]);
    expect(summary.availableCount).toBe(5);
    expect(summary.targetSlot?.slotId).toBe(2);
  });
});

describe('健康待办、提醒与通知规则', () => {
  it('通知列表不传递未选择的筛选参数', () => {
    expect(buildNotificationsPath({ pageNo: 2, pageSize: 50 })).toBe('/c/v1/notifications?pageNo=2&pageSize=50');
    expect(buildNotificationsPath({ patientId: 2, read: false })).toContain('patientId=2&read=false');
  });

  it('将后端通知类型转换为患者可读文案', () => {
    expect(getNotificationTypeText('MEDICATION_REMINDER')).toBe('用药提醒');
    expect(getNotificationTypeText('SYSTEM')).toBe('系统通知');
  });

  it('仅弹出最新未读的候补可预约挂号通知', () => {
    const notifications = [
      { id: 3, type: 'APPOINTMENT' as const, patientName: '张三', title: '候补号源可预约', content: '请在15分钟内完成预约。', read: false, createdAt: '2026-08-04T10:00:00+08:00' },
      { id: 2, type: 'APPOINTMENT' as const, patientName: '张三', title: '挂号支付成功', content: '订单已支付。', read: false, createdAt: '2026-08-04T09:00:00+08:00' },
    ];
    expect(findLatestWaitlistPromotionNotification(notifications)?.id).toBe(3);
    expect(findLatestWaitlistPromotionNotification([notifications[1]])).toBeUndefined();
  });

  it('通知已读网络重试复用首次生成的幂等键', () => {
    const first = resolveNotificationReadKey();
    expect(resolveNotificationReadKey(first)).toBe(first);
  });

  it('只聚合待处理项目并按时间升序关联就诊人', () => {
    const todos = buildHealthTodos([
      { patientId: 2, patientName: '小明', appointments: [{ id: 1, doctorName: '张医生', departmentName: '内科', startTime: '2026-08-05T10:00:00+08:00', status: 'COMPLETED', amountCent: 100 }], medicationPlans: [{ id: 2, drugName: '维生素', dosage: '1片', frequency: '每日一次', nextReminderAt: '2026-08-04T08:00:00+08:00', status: 'ACTIVE' }], followUps: [] },
      { patientId: 1, patientName: '张三', appointments: [{ id: 3, doctorName: '李医生', departmentName: '心内科', departmentLocation: '门诊楼2层201室', startTime: '2026-08-03T14:30:00+08:00', status: 'PAID', amountCent: 200 }], medicationPlans: [], followUps: [{ id: 4, type: '复诊', content: '携带检查报告', dueAt: '2026-08-06T09:00:00+08:00', status: 'CANCELLED' }] },
    ]);
    expect(todos.map((item) => [item.type, item.patientName])).toEqual([['APPOINTMENT', '张三'], ['MEDICATION', '小明']]);
    expect(todos[0].departmentLocation).toBe('门诊楼2层201室');
    expect(todos[1].departmentLocation).toBeUndefined();
  });

  it('仅按后端状态机提供用药和随访操作', () => {
    expect(getMedicationPlanActions('ACTIVE')).toEqual(['PAUSE', 'COMPLETE']);
    expect(getMedicationPlanActions('COMPLETED')).toEqual([]);
    expect(canConfirmFollowUp('PENDING_CONFIRM')).toBe(true);
    expect(canConfirmFollowUp('CONFIRMED')).toBe(false);
  });
});

describe('收货地址规则', () => {
  const addressForm = { receiverName: ' 张三 ', receiverPhone: '13800138000', province: 'HENAN', city: '郑州市', detailAddress: ' 中原路 1 号 ', district: '中原区' };

  it('地址列表使用已确认的后端路径', () => {
    expect(buildDeliveryAddressPath()).toBe('/c/v1/delivery-addresses');
  });

  it('校验必填地址字段、手机号与后端支持地区', () => {
    expect(validateDeliveryAddress({ ...addressForm, receiverPhone: '123' })).toBe('收件人手机号格式不正确');
    expect(validateDeliveryAddress({ ...addressForm, province: 'SICHUAN', city: '成都市' })).toBe('当前地区暂不支持配送');
  });

  it('提交时保留编辑地址的区县并清理文本两侧空白', () => {
    expect(buildDeliveryAddressPayload(addressForm)).toEqual({ receiverName: '张三', receiverPhone: '13800138000', province: 'HENAN', city: '郑州市', district: '中原区', detailAddress: '中原路 1 号' });
  });

  it('全国省级地区按拼音首字母排序，直辖市只返回本市', () => {
    expect(getDeliveryProvinces()[0].name).toBe('安徽省');
    expect(getDeliveryCities('BEIJING')).toEqual(['北京市']);
    expect(getDeliveryProvinces().every((item) => getDeliveryCities(item.code).length <= 15)).toBe(true);
  });

  it('地址写操作网络重试复用幂等键', () => {
    const first = resolveDeliveryIdempotencyKey();
    expect(resolveDeliveryIdempotencyKey(first)).toBe(first);
  });
});
