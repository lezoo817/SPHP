import type { DrugOrder, DrugOrderDetail } from '../typings/api';
import { formatAmount } from './medical';
import { drugOrderTabs, getLogisticsStatusText } from './pharmacy';

/** 物流进度阶段状态。 */
export type DrugOrderLogisticsStepState = 'done' | 'active' | 'pending';

/** 物流进度步骤展示对象。 */
export interface DrugOrderLogisticsStep {
  /** 步骤名称。 */
  label: string;
  /** 步骤展示状态。 */
  state: DrugOrderLogisticsStepState;
}

/** 横向物流进度的固定步骤。 */
const DRUG_ORDER_LOGISTICS_STEPS = ['待发货', '运输中', '待收货', '已收货'];

/**
 * 判断购药订单是否仍处于待支付状态。
 * @param status 后端购药订单状态
 * @returns 待支付时返回 true
 */
export function isPendingDrugOrder(status?: string): boolean {
  return status === 'PENDING_PAYMENT';
}

/**
 * 判断购药订单是否已经完成购买。
 * @param status 后端购药订单状态
 * @returns 已支付并可进入物流详情时返回 true
 */
export function isPurchasedDrugOrder(status?: string): boolean {
  return status === 'PAID';
}

/**
 * 从后端时间倒序订单中定位处方对应的最新已购买订单。
 * @param orders 当前就诊人的购药订单列表
 * @param prescriptionId 当前处方 ID
 * @returns 已支付订单；不存在时返回 undefined
 */
export function findPurchasedDrugOrder(orders: DrugOrder[], prescriptionId: number): DrugOrder | undefined {
  // 仅已支付订单允许从处方详情直接查看物流，待支付订单仍须先完成购买。
  return orders.find((order) => order.prescriptionId === prescriptionId && isPurchasedDrugOrder(order.status));
}

/**
 * 获取订单可用于模拟支付的支付单编号。
 * @param detail 订单详情返回的支付信息
 * @param paymentIdFromUrl 创建订单时地址栏携带的兼容支付单编号
 * @returns 有效支付单编号；无法解析时返回 undefined
 */
export function resolveDrugOrderPaymentId(detail: DrugOrderDetail | undefined, paymentIdFromUrl: number | undefined): number | undefined {
  const paymentId = detail?.payment?.id || paymentIdFromUrl;
  return Number.isInteger(paymentId) && paymentId! > 0 ? paymentId : undefined;
}

/**
 * 生成订单药品明细中的数量与单价文案。
 * @param quantity 后端订单快照中的药品数量
 * @param unitPriceCent 后端订单快照中的单价，单位为分
 * @returns 例如“2 x 28.00 元”的明细文案
 */
export function formatDrugOrderItemPrice(quantity: number, unitPriceCent: number): string {
  return `${Math.max(0, quantity)} x ${formatAmount(unitPriceCent)}`;
}

/**
 * 获取物流页应展示的状态文案。
 * @param detail 后端购药订单详情
 * @returns 后端尚未生成物流状态时，已支付订单显示“配送中”
 */
export function getDrugOrderLogisticsText(detail: DrugOrderDetail | undefined): string {
  const logisticsStatus = detail?.delivery?.logisticsStatus || detail?.logisticsStatus;
  if (logisticsStatus) return getLogisticsStatusText(logisticsStatus);
  return isPendingDrugOrder(detail?.status) ? '待支付' : '配送中';
}

/**
 * 将后端物流状态转换为横向进度步骤，兼容历史已发货状态。
 * @param logisticsStatus 后端物流状态
 * @returns 四阶段物流进度步骤
 */
export function getDrugOrderLogisticsSteps(logisticsStatus?: string): DrugOrderLogisticsStep[] {
  const statusIndex = ({
    PENDING_SHIPMENT: 0,
    SHIPPED: 1,
    IN_TRANSIT: 1,
    TO_RECEIVE: 2,
    RECEIVED: 3,
  } as Record<string, number>)[logisticsStatus || ''] ?? 0;
  return DRUG_ORDER_LOGISTICS_STEPS.map((label, index) => ({
    label,
    state: logisticsStatus === 'RECEIVED' || index < statusIndex ? 'done' : index === statusIndex ? 'active' : 'pending',
  }));
}

/**
 * 将时间格式化为上海时区的 YYYY/MM/DD HH:mm。
 * @param value ISO 时间或 Date 对象
 * @returns 固定格式时间；无效值时返回时间待确认
 */
export function formatDrugOrderLogisticsTime(value?: string | Date): string {
  const date = value instanceof Date ? value : value ? new Date(value) : undefined;
  if (!date || Number.isNaN(date.getTime())) return '时间待确认';
  const values = new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(date).reduce<Record<string, string>>((result, part) => {
    result[part.type] = part.value;
    return result;
  }, {});
  return `${values.year}/${values.month}/${values.day} ${values.hour}:${values.minute}`;
}

/**
 * 读取后端模拟物流返回的预计送达时间，不在 H5 端推算配送时长。
 * @param detail 购药订单详情
 * @returns YYYY/MM/DD HH:mm 格式的预计送达时间；后端未返回时为 undefined
 */
export function getDrugOrderExpectedDeliveryTime(detail: DrugOrderDetail | undefined): string | undefined {
  const expectedDeliveryAt = detail?.delivery?.expectedDeliveryAt;
  return expectedDeliveryAt ? formatDrugOrderLogisticsTime(expectedDeliveryAt) : undefined;
}

/**
 * 判断物流详情页面是否需要继续轮询服务端状态。
 * @param detail 购药订单详情
 * @returns 已支付且未确认收货时返回 true
 */
export function shouldPollDrugOrderLogistics(detail: DrugOrderDetail | undefined): boolean {
  const logisticsStatus = detail?.delivery?.logisticsStatus || detail?.logisticsStatus;
  return detail?.status === 'PAID' && logisticsStatus !== 'RECEIVED';
}

/**
 * 判断物流页是否应显示确认收货操作。
 * @param detail 后端购药订单详情
 * @returns 后端物流状态为待收货时返回 true
 */
export function canConfirmDrugOrderReceipt(detail: DrugOrderDetail | undefined): boolean {
  return detail?.delivery?.logisticsStatus === 'TO_RECEIVE' || detail?.logisticsStatus === 'TO_RECEIVE';
}

/**
 * 构建“我的订单”列表页地址，并保留当前筛选条件。
 * @param patientId 当前购药就诊人 ID
 * @param tab 当前订单状态 Tab
 * @param keyword 已提交的订单搜索词
 * @returns 可恢复订单列表状态的页面路径
 */
export function buildDrugOrderListPagePath(patientId: number, tab?: string, keyword?: string): string {
  const query = new URLSearchParams({ patientId: String(patientId) });
  if (tab) query.set('tab', tab);
  if (keyword?.trim()) query.set('keyword', keyword.trim());
  return `/pharmacy/orders?${query.toString()}`;
}

/**
 * 构建购药订单详情页地址。
 * @param drugOrderId 购药订单编号
 * @param returnTo 可选的订单列表回跳地址
 * @returns 携带列表来源上下文的订单详情路由
 */
export function buildDrugOrderDetailPath(drugOrderId: number, returnTo?: string): string {
  if (!returnTo) return `/pharmacy/order/${drugOrderId}`;
  return `/pharmacy/order/${drugOrderId}?${new URLSearchParams({ returnTo }).toString()}`;
}

/**
 * 从地址栏恢复安全的“我的订单”列表回跳地址。
 * @param returnTo 订单详情页接收的候选列表地址
 * @returns 仅包含受支持筛选条件的订单列表地址；非法值返回 undefined
 */
export function resolveDrugOrderListPagePath(returnTo: string | null): string | undefined {
  const url = parseInternalPharmacyPath(returnTo);
  if (!url || url.pathname !== '/pharmacy/orders') return undefined;
  const patientId = Number(url.searchParams.get('patientId'));
  if (!Number.isInteger(patientId) || patientId <= 0) return undefined;
  const tab = url.searchParams.get('tab') || undefined;
  const validTab = tab && drugOrderTabs.some((item) => item.key === tab) ? tab : undefined;
  return buildDrugOrderListPagePath(patientId, validTab, url.searchParams.get('keyword') || undefined);
}

/**
 * 从物流详情页恢复安全的订单详情回跳地址。
 * @param drugOrderId 当前购药订单编号
 * @param returnTo 物流页接收的候选订单详情地址
 * @returns 同一订单的详情地址；非法值回退至购药首页
 */
export function resolveDrugOrderDetailPagePath(drugOrderId: number, returnTo: string | null): string {
  const url = parseInternalPharmacyPath(returnTo);
  if (!url || url.pathname !== `/pharmacy/order/${drugOrderId}`) return '/pharmacy';
  // 订单详情仅恢复已校验的订单列表来源，避免物流页地址栏夹带任意跳转地址。
  return buildDrugOrderDetailPath(drugOrderId, resolveDrugOrderListPagePath(url.searchParams.get('returnTo')));
}

/**
 * 构建购药订单物流详情路径。
 * @param drugOrderId 购药订单编号
 * @param returnToOrder 可选的订单详情回跳地址
 * @returns 独立物流详情路由
 */
export function buildDrugOrderLogisticsPath(drugOrderId: number, returnToOrder?: string): string {
  if (!returnToOrder) return `/pharmacy/order/${drugOrderId}/logistics`;
  return `/pharmacy/order/${drugOrderId}/logistics?${new URLSearchParams({ returnTo: returnToOrder }).toString()}`;
}

/**
 * 将候选地址解析为受控的站内购药路径。
 * @param path 地址栏传入的候选路径
 * @returns 站内路径对应的 URL；跨站或非法值返回 undefined
 */
function parseInternalPharmacyPath(path: string | null): URL | undefined {
  if (!path?.startsWith('/')) return undefined;
  try {
    const url = new URL(path, 'https://sphp.local');
    return url.origin === 'https://sphp.local' ? url : undefined;
  } catch {
    return undefined;
  }
}
