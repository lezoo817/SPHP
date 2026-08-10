import type { DeliveryPharmacyRecommendation, DrugOrder, DrugOrderDetail, PageData, PharmacyInventory } from '../typings/api';
import { request } from './request';

/** 创建购药订单所需的患者、处方、药房与地址快照参数。 */
export interface DrugOrderCreatePayload {
  /** 当前就诊人 ID；缺失时由后端按本人处理。 */
  patientId?: number;
  /** 已批准处方 ID。 */
  prescriptionId: number;
  /** 用户选定的药房 ID。 */
  pharmacyId: number;
  /** 下单时使用的收货地址 ID。 */
  addressId: number;
}

/** 查询处方在院内药房中的可售库存。 */
export function getInventory(patientId: number | undefined, prescriptionId: number): Promise<PharmacyInventory[]> {
  return request(`/c/v1/pharmacies/inventory?patientId=${patientId || ''}&prescriptionId=${prescriptionId}`, { method: 'GET' });
}

/**
 * 查询指定地址下可配送的处方药房推荐。
 * @param patientId 当前就诊人 ID
 * @param prescriptionId 已批准处方 ID
 * @param addressId 当前收货地址 ID
 * @param sort 推荐排序方式，默认由后端推荐排序处理
 * @returns 可配送药房及价格、距离信息
 */
export function getPharmacyRecommendations(
  patientId: number | undefined,
  prescriptionId: number,
  addressId: number,
  sort = 'RECOMMENDED',
): Promise<DeliveryPharmacyRecommendation[]> {
  const params = new URLSearchParams({
    prescriptionId: String(prescriptionId),
    addressId: String(addressId),
    sort,
  });
  if (patientId) params.set('patientId', String(patientId));
  return request(`/c/v1/pharmacies/recommendations?${params.toString()}`, { method: 'GET' });
}

/**
 * 使用当前地址簿快照创建待支付购药订单。
 * @param payload 下单所需的患者、处方、药房与地址参数
 * @param key 写操作幂等键；重试时必须复用同一值
 * @returns 新建订单与对应支付单 ID
 */
export function createDrugOrder(payload: DrugOrderCreatePayload, key: string): Promise<{ drugOrderId: number; paymentId: number }> {
  return request('/c/v1/drug-orders', {
    method: 'POST',
    body: payload,
    headers: { 'X-Idempotency-Key': key },
  });
}

/** 购药订单列表查询条件。 */
export interface DrugOrderListQuery {
  /** 当前就诊人 ID。 */
  patientId?: number;
  /** 订单业务状态。 */
  status?: string;
  /** 配送物流状态。 */
  logisticsStatus?: string;
  /** 药房或药品关键字。 */
  keyword?: string;
  /** 页码，从 1 开始。 */
  pageNo?: number;
  /** 每页数量。 */
  pageSize?: number;
}

/**
 * 生成购药订单列表请求路径。
 * @param query 当前就诊人、状态与订单名称查询条件
 * @returns 已编码的接口相对路径
 */
export function buildDrugOrderListPath(query: DrugOrderListQuery = {}): string {
  const params = new URLSearchParams();
  if (query.patientId) params.set('patientId', String(query.patientId));
  if (query.status) params.set('status', query.status);
  if (query.logisticsStatus) params.set('logisticsStatus', query.logisticsStatus);
  if (query.keyword?.trim()) params.set('keyword', query.keyword.trim());
  params.set('pageNo', String(query.pageNo || 1));
  params.set('pageSize', String(query.pageSize || 20));
  return `/c/v1/drug-orders?${params.toString()}`;
}

/**
 * 查询当前就诊人的购药订单。
 * @param query 订单筛选和分页条件
 * @returns 后端返回的订单分页数据
 */
export function getDrugOrders(query: DrugOrderListQuery = {}): Promise<PageData<DrugOrder>> {
  return request(buildDrugOrderListPath(query), { method: 'GET' });
}

/**
 * 查询购药订单详情。
 * @param id 购药订单 ID
 * @returns 订单、支付、物流和地址快照详情
 */
export function getDrugOrder(id: number): Promise<DrugOrderDetail> {
  return request(`/c/v1/drug-orders/${id}`, { method: 'GET' });
}

/**
 * 取消待支付购药订单。
 * @param id 购药订单 ID
 * @param key 写操作幂等键；重试时必须复用同一值
 * @returns 无返回值
 */
export function cancelDrugOrder(id: number, key: string): Promise<void> {
  return request(`/c/v1/drug-orders/${id}/cancel`, {
    method: 'POST',
    headers: { 'X-Idempotency-Key': key },
  });
}

/**
 * 确认购药订单收货。
 * @param id 购药订单 ID
 * @param key 写操作幂等键；重试时必须复用同一值
 * @returns 无返回值
 */
export function confirmReceipt(id: number, key: string): Promise<void> {
  return request(`/c/v1/drug-orders/${id}/confirm-receipt`, {
    method: 'POST',
    headers: { 'X-Idempotency-Key': key },
  });
}
