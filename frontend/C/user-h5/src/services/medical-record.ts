import type { MedicalRecordDetail, MedicalRecordItem, PageData } from '../typings/api';
import { request } from './request';

/** 病历列表的分页查询条件。 */
export interface MedicalRecordListQuery {
  /** 当前就诊人 ID。 */
  patientId: number;
  /** 页码，从 1 开始。 */
  pageNo?: number;
  /** 每页数量，后端最大支持 100。 */
  pageSize?: number;
}

/**
 * 生成病历列表请求路径，避免空参数触发后端分页校验失败。
 * @param query 病历分页查询条件
 * @returns 已编码的 C 端病历列表路径
 */
export function buildMedicalRecordListPath({ patientId, pageNo = 1, pageSize = 100 }: MedicalRecordListQuery): string {
  const params = new URLSearchParams({ patientId: String(patientId), pageNo: String(pageNo), pageSize: String(pageSize) });
  return `/c/v1/medical-records?${params.toString()}`;
}

/**
 * 查询当前就诊人的已完成病历分页数据。
 * @param query 就诊人和分页参数
 * @returns 后端按完成时间倒序返回的病历页
 */
export function getMedicalRecords(query: MedicalRecordListQuery): Promise<PageData<MedicalRecordItem>> {
  return request(buildMedicalRecordListPath(query), { method: 'GET' });
}

/**
 * 构建病历详情请求路径。
 * @param consultId 问诊病历 ID
 * @returns 单份病历详情路径
 */
export function buildMedicalRecordDetailPath(consultId: number): string {
  return `/c/v1/medical-records/${consultId}`;
}

/**
 * 查询单份可见病历详情。
 * @param consultId 问诊病历 ID
 * @returns 当前账号有权查看的病历详情
 */
export function getMedicalRecord(consultId: number): Promise<MedicalRecordDetail> {
  return request(buildMedicalRecordDetailPath(consultId), { method: 'GET' });
}
