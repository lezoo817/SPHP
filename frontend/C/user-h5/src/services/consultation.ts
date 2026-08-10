import type { Consultation, ConsultationDetail, PageData, Prescription, PrescriptionDetail } from '../typings/api';
import { request } from './request';

/** 已批准处方列表的分页查询参数。 */
export interface PrescriptionListParams {
  /** 可选的当前就诊人编号。 */
  patientId?: number;
  /** 页号，默认第一页。 */
  pageNo?: number;
  /** 每页条数，默认二十条。 */
  pageSize?: number;
}

/** 提交无挂号在线预问诊所需的医生与病情描述。 */
export interface PreConsultationPayload {
  /** 接诊医生 ID。 */
  doctorId: number;
  /** 患者主诉，不能为空。 */
  chiefComplaint: string;
  /** 可选的现病史补充。 */
  historyOfPresentIllness?: string;
}

/** 游标查询问诊消息的可选条件。 */
export interface ConsultationMessageQuery {
  /** 仅返回该消息 ID 之后的消息。 */
  afterId?: number;
  /** 仅返回该消息 ID 之前的消息。 */
  beforeId?: number;
  /** 单次读取数量。 */
  size?: number;
}

/**
 * 向指定医生提交无挂号在线预问诊。
 * @param payload 医生和预问诊内容
 * @param key 幂等键；网络重试必须复用同一值
 * @returns 新建问诊记录 ID 与初始状态
 */
export function savePreConsultation(payload: PreConsultationPayload, key: string): Promise<{ consultationId: number; status: string }> {
  return request('/c/v1/consultations/pre-consultations', {
    method: 'POST',
    body: payload,
    headers: { 'X-Idempotency-Key': key },
  });
}

/**
 * 查询当前就诊人的问诊记录。
 * @param patientId 当前就诊人 ID；未传时由后端按本人处理
 * @returns 第一页问诊记录
 */
export function getConsultations(patientId?: number): Promise<PageData<Consultation>> {
  return request(`/c/v1/consultations?patientId=${patientId || ''}&pageNo=1&pageSize=20`, { method: 'GET' });
}

/**
 * 查询问诊详情和文字消息。
 * @param id 问诊记录 ID
 * @returns 医生信息、预问诊、消息与处方关联详情
 */
export function getConsultation(id: number): Promise<ConsultationDetail> {
  return request(`/c/v1/consultations/${id}`, { method: 'GET' });
}

/**
 * 发送患者文字消息。
 * @param id 问诊记录 ID
 * @param content 患者发送的文本内容
 * @param clientMessageId 客户端消息 ID，同时作为幂等键
 * @returns 已持久化的消息摘要
 */
export function sendConsultationMessage(
  id: number,
  content: string,
  clientMessageId: string,
): Promise<{ messageId: number; content: string; createdAt: string }> {
  return request(`/c/v1/consultations/${id}/messages`, {
    method: 'POST',
    body: { content, clientMessageId },
    headers: { 'X-Idempotency-Key': clientMessageId },
  });
}

/**
 * 游标查询问诊消息，用于断线重连后的可靠补拉。
 * @param id 问诊记录 ID
 * @param params 游标与单次读取数量
 * @returns 消息列表及是否仍有更多记录
 */
export function getConsultationMessages(
  id: number,
  params: ConsultationMessageQuery = {},
): Promise<{ messages: ConsultationDetail['messages']; hasMore: boolean }> {
  const query = new URLSearchParams();
  if (params.afterId) query.set('afterId', String(params.afterId));
  if (params.beforeId) query.set('beforeId', String(params.beforeId));
  if (params.size) query.set('size', String(params.size));
  return request(`/c/v1/consultations/${id}/messages${query.size ? `?${query.toString()}` : ''}`, { method: 'GET' });
}

/**
 * 构建已批准处方列表请求路径。
 * @param params 就诊人与分页参数
 * @returns 不包含空查询参数的接口路径
 */
export function buildPrescriptionsPath({ patientId, pageNo = 1, pageSize = 20 }: PrescriptionListParams = {}): string {
  const search = new URLSearchParams({ pageNo: String(pageNo), pageSize: String(pageSize) });
  // 未选择就诊人时由后端按当前账号处理，避免提交空字符串导致参数校验异常。
  if (patientId && patientId > 0) search.set('patientId', String(patientId));
  return `/c/v1/prescriptions?${search.toString()}`;
}

/**
 * 查询已批准处方。
 * @param params 就诊人与分页参数
 * @returns 已批准处方分页数据
 */
export function getPrescriptions(params: PrescriptionListParams = {}): Promise<PageData<Prescription>> {
  return request(buildPrescriptionsPath(params), { method: 'GET' });
}

/**
 * 查询已批准处方详情。
 * @param id 处方 ID
 * @returns 医生、药品及用法用量详情
 */
export function getPrescription(id: number): Promise<PrescriptionDetail> {
  return request(`/c/v1/prescriptions/${id}`, { method: 'GET' });
}
