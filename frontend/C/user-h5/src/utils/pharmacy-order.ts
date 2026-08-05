import type { DrugOrder, DrugOrderDetail } from '../typings/api';
import { formatAmount } from './medical';
import { getLogisticsStatusText } from './pharmacy';

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
 * 按支付成功首个物流节点和后端两段 30 秒流转计算预计收货时间。
 * @param detail 购药订单详情
 * @param now 缺少轨迹时的回退基准时间
 * @returns YYYY/MM/DD HH:mm 格式的预计收货时间
 */
export function getDrugOrderExpectedDeliveryTime(detail: DrugOrderDetail | undefined, now = new Date()): string {
  const traceTimes = detail?.delivery?.traces
    .map((trace) => new Date(trace.occurredAt).getTime())
    .filter((timestamp) => !Number.isNaN(timestamp)) || [];
  const startAt = traceTimes.length ? Math.min(...traceTimes) : now.getTime();
  return formatDrugOrderLogisticsTime(new Date(startAt + 60_000));
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
 * 构建购药订单物流详情路径。
 * @param drugOrderId 购药订单编号
 * @returns 独立物流详情路由
 */
export function buildDrugOrderLogisticsPath(drugOrderId: number): string {
  return `/pharmacy/order/${drugOrderId}/logistics`;
}
