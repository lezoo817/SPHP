import type { DrugOrderDetail } from '../typings/api';
import { formatAmount } from './medical';
import { getLogisticsStatusText } from './pharmacy';

/**
 * 判断购药订单是否仍处于待支付状态。
 * @param status 后端购药订单状态
 * @returns 待支付时返回 true
 */
export function isPendingDrugOrder(status?: string): boolean {
  return status === 'PENDING_PAYMENT';
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
 * 判断物流页是否应显示确认收货操作。
 * @param detail 后端购药订单详情
 * @returns 后端物流状态为待收货时返回 true
 */
export function canConfirmDrugOrderReceipt(detail: DrugOrderDetail | undefined): boolean {
  return detail?.delivery?.logisticsStatus === 'TO_RECEIVE' || detail?.logisticsStatus === 'TO_RECEIVE';
}

/**
 * 构建购药订单物流详情路径。
 * @param drugOrderId 购药订单编号
 * @returns 独立物流详情路由
 */
export function buildDrugOrderLogisticsPath(drugOrderId: number): string {
  return `/pharmacy/order/${drugOrderId}/logistics`;
}
