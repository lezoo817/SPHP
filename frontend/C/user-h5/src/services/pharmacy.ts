import type { DrugOrder, DrugOrderDetail, PageData, PharmacyInventory } from '../typings/api'; import { request } from './request';
/** 查询处方的院内药房库存。 */ export function getInventory(patientId:number|undefined,prescriptionId:number):Promise<PharmacyInventory[]>{return request(`/c/v1/pharmacies/inventory?patientId=${patientId||''}&prescriptionId=${prescriptionId}`,{method:'GET'});}
/** 创建固定演示地址的购药订单。 */ export function createDrugOrder(payload:{patientId?:number;prescriptionId:number;pharmacyId:number;deliveryAddress:string},key:string):Promise<{drugOrderId:number;paymentId:number}>{return request('/c/v1/drug-orders',{method:'POST',body:payload,headers:{'X-Idempotency-Key':key}});}
/** 购药订单列表查询条件。 */
export interface DrugOrderListQuery { patientId?:number; status?:string; logisticsStatus?:string; keyword?:string; pageNo?:number; pageSize?:number; }
/**
 * 生成购药订单列表请求路径。
 * @param query 当前就诊人、状态与订单名称查询条件
 * @returns 已编码的接口相对路径
 */
export function buildDrugOrderListPath(query:DrugOrderListQuery={}):string{const params=new URLSearchParams();if(query.patientId)params.set('patientId',String(query.patientId));if(query.status)params.set('status',query.status);if(query.logisticsStatus)params.set('logisticsStatus',query.logisticsStatus);if(query.keyword?.trim())params.set('keyword',query.keyword.trim());params.set('pageNo',String(query.pageNo||1));params.set('pageSize',String(query.pageSize||20));return `/c/v1/drug-orders?${params.toString()}`;}
/**
 * 查询当前就诊人的购药订单。
 * @param query 订单筛选和分页条件
 * @returns 后端返回的订单分页数据
 */
export function getDrugOrders(query:DrugOrderListQuery={}):Promise<PageData<DrugOrder>>{return request(buildDrugOrderListPath(query),{method:'GET'});}
/** 查询购药订单详情。 */ export function getDrugOrder(id:number):Promise<DrugOrderDetail>{return request(`/c/v1/drug-orders/${id}`,{method:'GET'});}
/** 取消待支付购药订单。 */ export function cancelDrugOrder(id:number,key:string):Promise<void>{return request(`/c/v1/drug-orders/${id}/cancel`,{method:'POST',headers:{'X-Idempotency-Key':key}});}
/** 确认购药订单收货。 */ export function confirmReceipt(id:number,key:string):Promise<void>{return request(`/c/v1/drug-orders/${id}/confirm-receipt`,{method:'POST',headers:{'X-Idempotency-Key':key}});}
