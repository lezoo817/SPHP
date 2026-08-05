import { MapPin, UserRound } from 'lucide-react';

/** 购药订单详情中展示就诊人与收货地址快照的组件属性。 */
interface OrderDeliveryCardProps {
  /** 当前处方关联的就诊人姓名。 */
  patientName?: string;
  /** 后端返回的脱敏就诊人手机号。 */
  patientPhone?: string;
  /** 下单时固化的收货地址快照。 */
  address?: string;
}

/**
 * 展示购药订单创建时的就诊人与收货地址快照，避免读取后续变更的地址簿资料。
 * @param props 卡片展示资料
 * @returns 就诊人和配送地址卡片
 */
export function OrderDeliveryCard({ patientName, patientPhone, address }: OrderDeliveryCardProps) {
  return <section className="pharmacy-order-delivery-card">
    <div className="pharmacy-order-delivery-card__patient"><UserRound size={20} /><div><b>就诊人</b><span>{patientName || '就诊人待确认'} {patientPhone || ''}</span></div></div>
    <div className="pharmacy-order-delivery-card__address"><MapPin size={20} /><div><b>送至</b><span>{address || '收货地址待确认'}</span></div></div>
  </section>;
}
