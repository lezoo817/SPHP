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
