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
import { buildPharmacyHomePath, buildPharmacyInventoryPath, buildPharmacyPrescriptionPath, getDrugOrderCardStatusText, isInvalidDrugOrder, matchesDrugOrderTab, resolvePharmacyPatientId } from './pharmacy';
import { hasSearchKeyword, matchesDepartmentKeyword, resolveInitialDepartment } from './home-search';
import { buildProfileUpdatePayload, normalizeProfileIdCardNo, resolveProfileIdempotencyKey, validateProfileForm } from './profile';
import { resolveMinePatientId } from '../models/mine-patient';
import { isSessionTokenExpired, type SessionState } from '../models/session';
import { buildDoctorPagePath, findDoctorById, getDoctorScheduleDates } from './doctor';
import { groupSlotsByHalfDay, summarizeHalfDaySlots } from './doctor';
import { buildAppointmentsPath } from '../services/registration';
import { buildNotificationsPath } from '../services/notification';
import { buildHealthTodos, canConfirmFollowUp, findLatestWaitlistPromotionNotification, formatMedicationReminderTimes, getMedicationPlanActions, getMedicationReminderAction, getNotificationTypeText, resolveNotificationListType, resolveNotificationReadKey } from './health-notification';
import { buildDeliveryAddressPath } from '../services/delivery-address';
import { buildDeliveryAddressPayload, getDeliveryAddressInvalidFields, getDeliveryCities, getDeliveryProvinces, resolveDeliveryIdempotencyKey, validateDeliveryAddress } from './delivery-address';
import { ASSISTANT_APPOINTMENT_REFRESH_INTERVAL_MILLIS, getAssistantAppointmentRecordStatusText, getAssistantTabs, getCurrentFlowAction, isCurrentAssistantFlow, shouldDisplayAssistantAppointmentRecord } from './assistant';
import { buildMedicalRecordDetailPath, buildMedicalRecordListPath } from '../services/medical-record';
import { buildLegacyReportRedirectPath, createMedicalRecordDisplayNumber, filterMedicalRecordsByDate, getRecentMedicalRecordRange, mergeMedicalRecordPages } from './medical-record';
import { canCancelPaidAppointment, isDuplicateDoctorAppointmentError } from './registration';
import { buildDoctorBookingStatusPath } from '../services/registration';
import { buildPrescriptionsPath } from '../services/consultation';
import { buildAssistantPrescriptionDetailPath, buildMinePrescriptionDetailPath, buildMinePrescriptionListPath, createPrescriptionDisplayNumber, filterPrescriptionsByDate, getPrescriptionDisplayNumber, getRecentPrescriptionRange, mergePrescriptionPages, type PrescriptionDisplayNumberStorage } from './prescription';
import { buildDrugOrderLogisticsPath, canConfirmDrugOrderReceipt, findPurchasedDrugOrder, formatDrugOrderItemPrice, formatDrugOrderLogisticsTime, getDrugOrderExpectedDeliveryTime, getDrugOrderLogisticsSteps, getDrugOrderLogisticsText, isPendingDrugOrder, resolveDrugOrderPaymentId, shouldPollDrugOrderLogistics } from './pharmacy-order';
import { filterAppointmentRecordsByDate, getRecentAppointmentRecordRange, matchesAppointmentRecordTab, mergeAppointmentRecordPages } from './appointment-record';
import { clearDismissedExpiredHealthTodos, dismissExpiredHealthTodo, getDismissedExpiredHealthTodoIds, isExpiredHealthTodoDismissed, type ExpiredHealthTodoStorage } from '../models/expired-health-todo';
import type { Appointment } from '../typings/api';

describe('前端表单与联调规则', () => {
  it('拒绝长度不足的登录账号和密码', () => {
    expect(validateAccount('abc')).toBe('账号长度应为 4 至 32 位');
    expect(validatePassword('1234567')).toBe('密码长度应为 8 至 64 位');
  });

  it('禁止提交本人关系', () => {
    expect(validateFamilyMember({ name: '张三', relation: 'SELF' })).toBe('不能新增或编辑本人资料');
  });

  it('新增成员必须填写合法身份证号，编辑留空则保留原值', () => {
    expect(validateFamilyMember({ name: '张三', relation: 'CHILD', idCardNo: undefined }, true)).toBe('请填写身份证号');
    expect(validateFamilyMember({ name: '张三', relation: 'CHILD', idCardNo: '11010519491231002x' }, true)).toBeUndefined();
    expect(validateFamilyMember({ name: '张三', relation: 'CHILD', idCardNo: undefined }, false)).toBeUndefined();
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
    expect(isDuplicateDoctorAppointmentError({ code: 'A0506', message: '当前已有该医生待就诊挂号，不可重复预约' })).toBe(true);
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
    expect(buildPharmacyHomePath(20001)).toBe('/pharmacy?patientId=20001');
    expect(buildPharmacyHomePath()).toBe('/pharmacy');
    expect(buildPharmacyPrescriptionPath(13001, 20001)).toBe('/pharmacy/prescription/13001?patientId=20001');
    expect(buildPharmacyPrescriptionPath(13001, 20001, undefined, 30001)).toBe('/pharmacy/prescription/13001?patientId=20001&drugOrderId=30001');
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

  it('仅在号源结束前展示待支付或待就诊的当前流程', () => {
    const now = Date.parse('2026-08-06T10:00:00+08:00');
    const appointment = { id: 1, doctorName: '陈医生', departmentName: '内科', startTime: '2026-08-06T09:30:00+08:00', endTime: '2026-08-06T10:30:00+08:00', status: 'PAID' as const, amountCent: 100 };

    expect(isCurrentAssistantFlow(appointment, now)).toBe(true);
    expect(isCurrentAssistantFlow({ ...appointment, endTime: '2026-08-06T10:00:00+08:00' }, now)).toBe(false);
    expect(isCurrentAssistantFlow({ ...appointment, status: 'COMPLETED' }, now)).toBe(false);
  });

  it('挂号记录仅保留就诊完成和未结束的待就诊订单', () => {
    const now = Date.parse('2026-08-06T10:00:00+08:00');
    const appointment = { id: 1, doctorName: '陈医生', departmentName: '内科', startTime: '2026-08-06T09:30:00+08:00', endTime: '2026-08-06T10:30:00+08:00', status: 'PAID' as const, amountCent: 100 };

    expect(shouldDisplayAssistantAppointmentRecord(appointment, now)).toBe(true);
    expect(shouldDisplayAssistantAppointmentRecord({ ...appointment, status: 'COMPLETED' }, now)).toBe(true);
    expect(shouldDisplayAssistantAppointmentRecord({ ...appointment, status: 'UNPAID' }, now)).toBe(false);
    expect(shouldDisplayAssistantAppointmentRecord({ ...appointment, status: 'CANCELLED' }, now)).toBe(false);
    expect(shouldDisplayAssistantAppointmentRecord({ ...appointment, status: 'NO_SHOW' }, now)).toBe(false);
    expect(shouldDisplayAssistantAppointmentRecord({ ...appointment, endTime: '2026-08-06T10:00:00+08:00' }, now)).toBe(false);
    expect(getAssistantAppointmentRecordStatusText('PAID')).toBe('待就诊');
    expect(getAssistantAppointmentRecordStatusText('COMPLETED')).toBe('就诊完成');
  });

  it('就诊助手每三十秒静默刷新挂号状态', () => {
    expect(ASSISTANT_APPOINTMENT_REFRESH_INTERVAL_MILLIS).toBe(30000);
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
    expect(buildAppointmentsPath(1, undefined, 100, 2)).toBe('/c/v1/appointments?pageNo=2&pageSize=100&patientId=1');
  });

  it('将挂号订单状态转换为患者可理解的中文文案', () => {
    expect(getAppointmentStatusText('PAID')).toBe('支付完成');
    expect(getAppointmentStatusText('NO_SHOW')).toBe('未到诊');
    expect(getAppointmentStatusText('CANCELLED')).toBe('支付取消');
  });

  it('仅为尚未开始的已支付挂号展示取消入口', () => {
    const now = Date.parse('2026-08-06T10:00:00+08:00');
    expect(canCancelPaidAppointment('PAID', '2026-08-06T10:01:00+08:00', now)).toBe(true);
    expect(canCancelPaidAppointment('PAID', '2026-08-06T10:00:00+08:00', now)).toBe(false);
    expect(canCancelPaidAppointment('UNPAID', '2026-08-06T10:01:00+08:00', now)).toBe(false);
  });
});

describe('我的就诊记录查询规则', () => {
  const appointments: Appointment[] = [
    { id: 1, doctorName: '张医生', departmentName: '内科', departmentLocation: '门诊楼一层', startTime: '2026-08-03T08:00:00+08:00', status: 'COMPLETED', amountCent: 100 },
    { id: 2, doctorName: '李医生', departmentName: '外科', startTime: '2026-08-04T09:00:00+08:00', status: 'NO_SHOW', amountCent: 200 },
    { id: 3, doctorName: '王医生', departmentName: '骨科', startTime: '2026-08-05T10:00:00+08:00', status: 'CANCELLED', amountCent: 300 },
    { id: 4, doctorName: '赵医生', departmentName: '儿科', startTime: '2026-08-06T11:00:00+08:00', status: 'PAID', amountCent: 400 },
  ];

  it('生成最近 30/90/180 天的预约日期范围', () => {
    const now = new Date('2026-08-05T10:00:00+08:00');
    expect(getRecentAppointmentRecordRange(30, now)).toEqual({ startDate: '2026-07-07', endDate: '2026-08-05' });
    expect(getRecentAppointmentRecordRange(90, now).startDate).toBe('2026-05-08');
    expect(getRecentAppointmentRecordRange(180, now).startDate).toBe('2026-02-07');
  });

  it('仅将完成、未到诊和取消记录归入对应 Tab', () => {
    expect(appointments.filter((item) => matchesAppointmentRecordTab(item, 'COMPLETED')).map((item) => item.id)).toEqual([1]);
    expect(appointments.filter((item) => matchesAppointmentRecordTab(item, 'INVALID')).map((item) => item.id)).toEqual([2, 3]);
  });

  it('按预约日期筛选并按预约时间倒序排列', () => {
    expect(filterAppointmentRecordsByDate(appointments, { startDate: '2026-08-03', endDate: '2026-08-05' }).map((item) => item.id)).toEqual([3, 2, 1]);
    expect(filterAppointmentRecordsByDate(appointments, { startDate: '2026-08-06', endDate: '2026-08-05' })).toEqual([]);
  });

  it('加载更多时按挂号订单 ID 去重，并以新页记录为准', () => {
    const merged = mergeAppointmentRecordPages([appointments[0]], [{ ...appointments[0], departmentLocation: '门诊楼二层' }, appointments[1]]);
    expect(merged).toHaveLength(2);
    expect(merged.find((item) => item.id === 1)?.departmentLocation).toBe('门诊楼二层');
  });
});

describe('购药订单展示规则', () => {
  it('运输中同时包含已发货和运输中状态，失效订单独立归类', () => {
    expect(matchesDrugOrderTab({ id: 1, prescriptionId: 11, orderName: '阿莫西林', pharmacyName: '健康药房', status: 'PAID', logisticsStatus: 'SHIPPED', amountCent: 100 }, 'TRANSIT')).toBe(true);
    expect(matchesDrugOrderTab({ id: 2, prescriptionId: 12, orderName: '维生素', pharmacyName: '健康药房', status: 'PAID', logisticsStatus: 'TO_RECEIVE', amountCent: 100 }, 'TRANSIT')).toBe(false);
    const expiredOrder = { id: 3, prescriptionId: 13, orderName: '布洛芬', pharmacyName: '健康药房', status: 'EXPIRED', logisticsStatus: 'PENDING_SHIPMENT', amountCent: 100 };
    expect(matchesDrugOrderTab(expiredOrder, 'INVALID')).toBe(true);
    expect(matchesDrugOrderTab(expiredOrder, 'TRANSIT')).toBe(false);
    expect(isInvalidDrugOrder(expiredOrder)).toBe(true);
    expect(getDrugOrderCardStatusText(expiredOrder)).toBe('已失效');
  });

  it('订单名称关键词经过编码并传递给列表接口', () => {
    expect(buildDrugOrderListPath({ patientId: 20001, keyword: '阿莫 西林', pageSize: 100 })).toContain('keyword=%E9%98%BF%E8%8E%AB+%E8%A5%BF%E6%9E%97');
  });

  it('待支付订单只进入购买弹窗，药品明细展示数量和单价', () => {
    expect(isPendingDrugOrder('PENDING_PAYMENT')).toBe(true);
    expect(isPendingDrugOrder('PAID')).toBe(false);
    expect(formatDrugOrderItemPrice(2, 2800)).toBe('2 x 28.00 元');
  });

  it('支付单优先使用详情返回值，并可回退到创建订单上下文', () => {
    const detail = { id: 1, prescriptionId: 101, orderName: '药品订单', pharmacyName: '药房', status: 'PENDING_PAYMENT', amountCent: 100, pharmacy: { id: 1, name: '药房' }, payment: { id: 99, status: 'PENDING' }, items: [] };
    expect(resolveDrugOrderPaymentId(detail, 88)).toBe(99);
    expect(resolveDrugOrderPaymentId({ ...detail, payment: undefined }, 88)).toBe(88);
  });

  it('支付后无真实物流状态时显示配送中，并按后端状态允许确认收货', () => {
    const detail = { id: 1, prescriptionId: 101, orderName: '药品订单', pharmacyName: '药房', status: 'PAID', amountCent: 100, pharmacy: { id: 1, name: '药房' }, items: [] };
    expect(getDrugOrderLogisticsText(detail)).toBe('配送中');
    expect(canConfirmDrugOrderReceipt({ ...detail, delivery: { address: '演示地址', logisticsStatus: 'TO_RECEIVE', traces: [] } })).toBe(true);
    expect(buildDrugOrderLogisticsPath(1001)).toBe('/pharmacy/order/1001/logistics');
  });

  it('处方只关联已支付订单，并使用该订单进入物流详情', () => {
    const purchased = findPurchasedDrugOrder([
      { id: 1, prescriptionId: 101, orderName: '布洛芬', pharmacyName: '药房', status: 'PENDING_PAYMENT', amountCent: 2800 },
      { id: 2, prescriptionId: 101, orderName: '布洛芬', pharmacyName: '药房', status: 'PAID', amountCent: 2800 },
    ], 101);
    expect(purchased?.id).toBe(2);
    expect(buildDrugOrderLogisticsPath(purchased!.id)).toBe('/pharmacy/order/2/logistics');
  });

  it('物流进度兼容已发货，并按四阶段标记当前步骤', () => {
    expect(getDrugOrderLogisticsSteps('PENDING_SHIPMENT').map((item) => item.state)).toEqual(['active', 'pending', 'pending', 'pending']);
    expect(getDrugOrderLogisticsSteps('SHIPPED').map((item) => item.state)).toEqual(['done', 'active', 'pending', 'pending']);
    expect(getDrugOrderLogisticsSteps('TO_RECEIVE').map((item) => item.state)).toEqual(['done', 'done', 'active', 'pending']);
    expect(getDrugOrderLogisticsSteps('RECEIVED').map((item) => item.state)).toEqual(['done', 'done', 'done', 'done']);
  });

  it('预计送达时间只展示后端模拟物流返回值', () => {
    const detail = { id: 1, prescriptionId: 101, orderName: '药品订单', pharmacyName: '药房', status: 'PAID', amountCent: 100, pharmacy: { id: 1, name: '药房' }, items: [], delivery: { address: '演示地址', logisticsStatus: 'PENDING_SHIPMENT', expectedDeliveryAt: '2026-08-05T10:01:00+08:00', traces: [{ node: '支付成功，等待药房发货', occurredAt: '2026-08-05T10:00:00+08:00' }] } };
    expect(formatDrugOrderLogisticsTime('2026-08-05T10:00:00+08:00')).toBe('2026/08/05 10:00');
    expect(getDrugOrderExpectedDeliveryTime(detail)).toBe('2026/08/05 10:01');
    expect(getDrugOrderExpectedDeliveryTime({ ...detail, delivery: { ...detail.delivery, expectedDeliveryAt: undefined } })).toBeUndefined();
  });

  it('仅已支付且未收货订单继续进行物流详情轮询', () => {
    const detail = { id: 1, prescriptionId: 101, orderName: '药品订单', pharmacyName: '药房', status: 'PAID', amountCent: 100, pharmacy: { id: 1, name: '药房' }, items: [], delivery: { address: '演示地址', logisticsStatus: 'TO_RECEIVE', traces: [] } };
    expect(shouldPollDrugOrderLogistics(detail)).toBe(true);
    expect(shouldPollDrugOrderLogistics({ ...detail, delivery: { ...detail.delivery, logisticsStatus: 'RECEIVED' } })).toBe(false);
    expect(shouldPollDrugOrderLogistics({ ...detail, status: 'PENDING_PAYMENT' })).toBe(false);
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
  const values = { name: ' 张三 ', gender: 'MALE' as const, birthday: '2000-01-01', phone: '', idCardNo: '', emergencyContact: '' };

  it('校验姓名和手机号格式', () => {
    expect(validateProfileForm({ ...values, name: ' ' })).toBe('请填写姓名');
    expect(validateProfileForm({ ...values, phone: '123' })).toBe('手机号格式不正确');
    expect(validateProfileForm({ ...values, idCardNo: 'invalid' })).toBe('身份证号格式不正确');
  });

  it('不提交空白的敏感资料字段', () => {
    expect(buildProfileUpdatePayload(values)).toEqual({ name: '张三', gender: 'MALE', birthday: '2000-01-01' });
  });

  it('规范化身份证号并提交大写校验位', () => {
    expect(normalizeProfileIdCardNo('11010519491231002x')).toBe('11010519491231002X');
    expect(buildProfileUpdatePayload({ ...values, idCardNo: '11010519491231002x' }).idCardNo).toBe('11010519491231002X');
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

describe('我的处方查询规则', () => {
  it('处方列表请求省略未选择的就诊人参数', () => {
    expect(buildPrescriptionsPath({ pageNo: 2, pageSize: 100 })).toBe('/c/v1/prescriptions?pageNo=2&pageSize=100');
    expect(buildPrescriptionsPath({ patientId: 20001 })).toContain('patientId=20001');
  });

  it('最近处方日期范围包含当天且支持日期筛选', () => {
    const range = getRecentPrescriptionRange(30, new Date(2026, 7, 5));
    expect(range).toEqual({ startDate: '2026-07-07', endDate: '2026-08-05' });
    const filtered = filterPrescriptionsByDate([
      { id: 1, consultationId: 11, doctorName: '张医生', status: 'APPROVED', issuedAt: '2026-08-03T10:00:00+08:00' },
      { id: 2, consultationId: 12, doctorName: '李医生', status: 'APPROVED', issuedAt: '2026-08-04T10:00:00+08:00' },
      { id: 3, consultationId: 13, doctorName: '王医生', status: 'APPROVED', issuedAt: '2026-07-01T10:00:00+08:00' },
    ], range);
    expect(filtered.map((item) => item.id)).toEqual([2, 1]);
  });

  it('处方分页按编号去重，并保留新页中的更新数据', () => {
    const records = mergePrescriptionPages(
      [{ id: 1, consultationId: 11, doctorName: '张医生', status: 'APPROVED', issuedAt: '2026-08-01T10:00:00+08:00' }],
      [{ id: 1, consultationId: 11, doctorName: '张主任', status: 'APPROVED', issuedAt: '2026-08-01T10:00:00+08:00' }, { id: 2, consultationId: 12, doctorName: '李医生', status: 'APPROVED', issuedAt: '2026-08-02T10:00:00+08:00' }],
    );
    expect(records).toHaveLength(2);
    expect(records.find((item) => item.id === 1)?.doctorName).toBe('张主任');
  });

  it('处方详情往返保留患者和日期筛选上下文', () => {
    const path = buildMinePrescriptionDetailPath(1001, 2001, { startDate: '2026-07-01', endDate: '2026-08-05' }, '2026-08-05T10:00:00+08:00');
    expect(path).toBe('/assistant/prescription/1001?source=mine-prescriptions&startDate=2026-07-01&endDate=2026-08-05&patientId=2001&issuedAt=2026-08-05T10%3A00%3A00%2B08%3A00');
    expect(buildMinePrescriptionListPath(new URLSearchParams(path.split('?')[1]))).toBe('/mine/prescriptions?patientId=2001&startDate=2026-07-01&endDate=2026-08-05');
    expect(buildAssistantPrescriptionDetailPath(1001, 2001, '2026-08-05T10:00:00+08:00')).toBe('/assistant/prescription/1001?source=assistant&patientId=2001&issuedAt=2026-08-05T10%3A00%3A00%2B08%3A00');
  });

  it('处方展示编号使用开具时间戳和四位随机尾号', () => {
    expect(createPrescriptionDisplayNumber('2026-08-05T10:00:00+08:00', 7)).toBe(`${Date.parse('2026-08-05T10:00:00+08:00')}0007`);
    expect(createPrescriptionDisplayNumber('2026-08-05T10:00:00+08:00', 12345)).toBe(`${Date.parse('2026-08-05T10:00:00+08:00')}9999`);
  });

  it('同一会话内同处方复用展示编号，不同处方独立生成', () => {
    const values = new Map<string, string>();
    const storage: PrescriptionDisplayNumberStorage = {
      getItem: (key) => values.get(key) || null,
      setItem: (key, value) => { values.set(key, value); },
    };
    const first = getPrescriptionDisplayNumber(1, '2026-08-05T10:00:00+08:00', 12, storage);
    expect(getPrescriptionDisplayNumber(1, '2026-08-05T10:00:00+08:00', 99, storage)).toBe(first);
    expect(getPrescriptionDisplayNumber(2, '2026-08-05T10:00:00+08:00', 34, storage)).not.toBe(first);
  });
});

describe('健康待办、提醒与通知规则', () => {
  it('关闭过期待办后在当前会话内持续隐藏，并在清除会话时移除标识', () => {
    const values = new Map<string, string>();
    const storage: ExpiredHealthTodoStorage = {
      getItem: (key) => values.get(key) || null,
      setItem: (key, value) => { values.set(key, value); },
      removeItem: (key) => { values.delete(key); },
    };
    const expiredTodo = { type: 'APPOINTMENT', patientId: 1, id: 7001 };

    expect(getDismissedExpiredHealthTodoIds(storage)).toEqual([]);
    const dismissedIds = dismissExpiredHealthTodo(expiredTodo, storage);
    expect(isExpiredHealthTodoDismissed(expiredTodo, dismissedIds)).toBe(true);
    expect(dismissExpiredHealthTodo(expiredTodo, storage)).toEqual(dismissedIds);
    clearDismissedExpiredHealthTodos(storage);
    expect(getDismissedExpiredHealthTodoIds(storage)).toEqual([]);
  });

  it('通知列表不传递未选择的筛选参数', () => {
    expect(buildNotificationsPath({ pageNo: 2, pageSize: 50 })).toBe('/c/v1/notifications?pageNo=2&pageSize=50');
    expect(buildNotificationsPath({ patientId: 2, read: false })).toContain('patientId=2&read=false');
    expect(buildNotificationsPath({ type: 'LOGISTICS' })).toContain('type=LOGISTICS');
  });

  it('将后端通知类型转换为患者可读文案', () => {
    expect(getNotificationTypeText('MEDICATION_REMINDER')).toBe('用药提醒');
    expect(getNotificationTypeText('LOGISTICS')).toBe('物流通知');
    expect(getNotificationTypeText('SYSTEM')).toBe('系统通知');
  });

  it('通知分类将全部分类与后端类型条件正确对应', () => {
    expect(resolveNotificationListType('ALL')).toBeUndefined();
    expect(resolveNotificationListType('LOGISTICS')).toBe('LOGISTICS');
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
      { patientId: 2, patientName: '小明', appointments: [{ id: 1, doctorName: '张医生', departmentName: '内科', startTime: '2026-08-05T10:00:00+08:00', status: 'COMPLETED', amountCent: 100 }], medicationPlans: [{ id: 2, drugName: '维生素', dosage: '1片', frequency: '每日一次', nextReminderAt: '2026-08-04T08:00:00+08:00', reminderEnabled: true, reminderTimes: ['08:00'], status: 'ACTIVE' }], followUps: [] },
      { patientId: 1, patientName: '张三', appointments: [{ id: 3, doctorName: '李医生', departmentName: '心内科', departmentLocation: '门诊楼2层201室', startTime: '2026-08-03T14:30:00+08:00', endTime: '2026-08-03T15:00:00+08:00', status: 'PAID', amountCent: 200 }], medicationPlans: [], followUps: [{ id: 4, type: '复诊', content: '携带检查报告', dueAt: '2026-08-06T09:00:00+08:00', status: 'CANCELLED' }] },
    ], Date.parse('2026-08-06T10:00:00+08:00'));
    expect(todos.map((item) => [item.type, item.patientName])).toEqual([['APPOINTMENT', '张三'], ['MEDICATION', '小明']]);
    expect(todos[0].departmentLocation).toBe('门诊楼2层201室');
    expect(todos[0].isExpired).toBe(true);
    expect(todos[0].detail).toBe('已过期');
    expect(todos[1].departmentLocation).toBeUndefined();
  });

  it('挂号开始后但结束前仍保持待就诊状态', () => {
    const todos = buildHealthTodos([
      { patientId: 1, patientName: '张三', appointments: [{ id: 5, doctorName: '王医生', departmentName: '骨科', startTime: '2026-08-06T09:00:00+08:00', endTime: '2026-08-06T10:00:00+08:00', status: 'PAID', amountCent: 200 }], medicationPlans: [], followUps: [] },
    ], Date.parse('2026-08-06T09:30:00+08:00'));

    expect(todos[0].isExpired).toBe(false);
    expect(todos[0].detail).toBe('挂号待就诊');
  });

  it('仅按后端状态机提供用药和随访操作', () => {
    expect(getMedicationPlanActions('ACTIVE')).toEqual(['PAUSE', 'COMPLETE']);
    expect(getMedicationPlanActions('COMPLETED')).toEqual([]);
    expect(canConfirmFollowUp('PENDING_CONFIRM')).toBe(true);
    expect(canConfirmFollowUp('CONFIRMED')).toBe(false);
  });

  it('仅执行中计划显示提醒开关，并使用后端返回的固定时刻', () => {
    expect(getMedicationReminderAction({ status: 'ACTIVE', reminderEnabled: false })).toBe('ENABLE_REMINDER');
    expect(getMedicationReminderAction({ status: 'ACTIVE', reminderEnabled: true })).toBe('DISABLE_REMINDER');
    expect(getMedicationReminderAction({ status: 'PAUSED', reminderEnabled: true })).toBeUndefined();
    expect(getMedicationReminderAction({ status: 'COMPLETED', reminderEnabled: false })).toBeUndefined();
    expect(formatMedicationReminderTimes(['08:00', '20:00'])).toBe('08:00、20:00');
    expect(formatMedicationReminderTimes([])).toBe('');
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

  it('保存尝试后同时标红全部缺失或格式不正确的必填项', () => {
    expect(getDeliveryAddressInvalidFields({ receiverName: '', receiverPhone: '', province: '', city: '', detailAddress: '' })).toEqual(['region', 'detailAddress', 'receiverName', 'receiverPhone']);
    expect(getDeliveryAddressInvalidFields({ ...addressForm, receiverPhone: '123', detailAddress: '' })).toEqual(['detailAddress', 'receiverPhone']);
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
