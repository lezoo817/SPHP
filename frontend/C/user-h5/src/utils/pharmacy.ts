import type { DrugOrder } from '../typings/api';

/** 购药订单页面可切换的物流分类。 */
export type DrugOrderTab = 'ALL' | 'TRANSIT' | 'TO_RECEIVE' | 'RECEIVED';

/** 物流分类的页面展示信息。 */
export const drugOrderTabs:{ key:DrugOrderTab; label:string }[]=[
  { key:'ALL',label:'全部订单' },
  { key:'TRANSIT',label:'运输中' },
  { key:'TO_RECEIVE',label:'待收货' },
  { key:'RECEIVED',label:'已收货' },
];

/**
 * 判断订单是否属于指定物流分类。
 * @param order 后端订单列表项
 * @param tab 页面当前分类
 * @returns 是否应在当前 Tab 展示
 */
export function matchesDrugOrderTab(order:DrugOrder,tab:DrugOrderTab):boolean{if(tab==='ALL')return true;if(tab==='TRANSIT')return order.logisticsStatus==='SHIPPED'||order.logisticsStatus==='IN_TRANSIT';if(tab==='TO_RECEIVE')return order.logisticsStatus==='TO_RECEIVE';return order.logisticsStatus==='RECEIVED';}

/**
 * 获取面向患者的物流状态文案。
 * @param logisticsStatus 后端物流状态编码
 * @returns 页面显示文案
 */
export function getLogisticsStatusText(logisticsStatus?:string):string{return ({PENDING_SHIPMENT:'待发货',SHIPPED:'已发货',IN_TRANSIT:'运输中',TO_RECEIVE:'待收货',RECEIVED:'已收货'} as Record<string,string>)[logisticsStatus||'']||'物流待更新';}

/**
 * 从购药页面查询参数中解析有效就诊人 ID。
 * @param patientIdText URL 中的 patientId 文本
 * @returns 正整数就诊人 ID；缺失或非法时返回 undefined
 */
export function resolvePharmacyPatientId(patientIdText: string | null): number | undefined {
  const patientId = Number(patientIdText);
  return Number.isInteger(patientId) && patientId > 0 ? patientId : undefined;
}

/**
 * 构建购药首页路径，并保留该模块独立选择的就诊人。
 * @param patientId 当前购药页本地就诊人 ID
 * @returns 带可选就诊人上下文的购药首页路径
 */
export function buildPharmacyHomePath(patientId?: number): string {
  return Number.isInteger(patientId) && patientId! > 0 ? `/pharmacy?patientId=${patientId}` : '/pharmacy';
}

/**
 * 构建购药处方详情页面路径。
 * @param prescriptionId 处方 ID
 * @param patientId 当前购药页本地就诊人 ID
 * @param issuedAt 可选的处方开具时间，用于详情接口缺字段时展示
 * @param drugOrderId 已购买订单 ID，用于处方详情跳转物流
 * @returns 携带就诊人与开具时间上下文的处方详情路径
 */
export function buildPharmacyPrescriptionPath(prescriptionId: number, patientId: number, issuedAt?: string, drugOrderId?: number): string {
  const search = new URLSearchParams({ patientId: String(patientId) });
  if (issuedAt) search.set('issuedAt', issuedAt);
  if (Number.isInteger(drugOrderId) && drugOrderId! > 0) search.set('drugOrderId', String(drugOrderId));
  return `/pharmacy/prescription/${prescriptionId}?${search.toString()}`;
}

/**
 * 构建附近有货药店页面路径。
 * @param prescriptionId 处方 ID
 * @param patientId 当前购药页本地就诊人 ID
 * @param issuedAt 可选的处方开具时间，用于库存页返回详情时恢复展示
 * @returns 携带就诊人与开具时间上下文的库存页面路径
 */
export function buildPharmacyInventoryPath(prescriptionId: number, patientId: number, issuedAt?: string): string {
  const search = new URLSearchParams({ patientId: String(patientId) });
  if (issuedAt) search.set('issuedAt', issuedAt);
  return `/pharmacy/prescription/${prescriptionId}/inventory?${search.toString()}`;
}
