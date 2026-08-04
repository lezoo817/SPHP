import type { PageData, ReportDetail, ReportInterpretation, ReportItem } from '../typings/api';
import { request } from './request';

/** 报告列表的分页查询条件。 */
export interface ReportListQuery {
  /** 当前就诊人 ID。 */
  patientId: number;
  /** 页码，从 1 开始。 */
  pageNo?: number;
  /** 每页数量，后端最大支持 100。 */
  pageSize?: number;
}

/**
 * 生成报告列表请求路径，避免空参数触发后端分页校验失败。
 * @param query 报告分页查询条件
 * @returns 已编码的 C 端报告列表路径
 */
export function buildReportListPath({ patientId, pageNo = 1, pageSize = 100 }: ReportListQuery): string {
  const params = new URLSearchParams({ patientId: String(patientId), pageNo: String(pageNo), pageSize: String(pageSize) });
  return `/c/v1/reports?${params.toString()}`;
}

/**
 * 查询当前就诊人的已完成报告分页数据。
 * @param query 就诊人和分页参数
 * @returns 后端按完成时间倒序返回的报告页
 */
export function getReports(query: ReportListQuery): Promise<PageData<ReportItem>> {
  return request(buildReportListPath(query), { method: 'GET' });
}

/**
 * 查询单份可见报告的医生病历正文。
 * @param reportId 报告 ID
 * @returns 当前账号有权查看的报告详情
 */
export function getReport(reportId: number): Promise<ReportDetail> {
  return request(`/c/v1/reports/${reportId}`, { method: 'GET' });
}

/**
 * 查询已经准备完成的报告解读。
 * @param reportId 报告 ID
 * @returns 已生成的解读内容；未准备好时后端返回 409
 */
export function getReportInterpretation(reportId: number): Promise<ReportInterpretation> {
  return request(`/c/v1/reports/${reportId}/interpretation`, { method: 'GET' });
}
