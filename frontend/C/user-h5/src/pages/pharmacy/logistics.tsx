import { useEffect, useState } from 'react';
import { MapPin, PackageCheck, Truck } from 'lucide-react';
import { useNavigate, useParams } from 'umi';
import { PageHeader } from '../../components/PageHeader';
import { confirmReceipt, getDrugOrder } from '../../services/pharmacy';
import type { DrugOrderDetail } from '../../typings/api';
import { createIdempotencyKey, getApiErrorMessage } from '../../utils/form';
import { formatAmount, getDemoArrival } from '../../utils/medical';
import { canConfirmDrugOrderReceipt, formatDrugOrderItemPrice, getDrugOrderLogisticsText, isPendingDrugOrder } from '../../utils/pharmacy-order';
import { getSession } from '../../models/session';

/** 展示支付完成后的购药配送状态、药品明细和确认收货操作。 */
export default function DrugOrderLogisticsPage() {
  const { drugOrderId: drugOrderIdText } = useParams();
  const navigate = useNavigate();
  const drugOrderId = Number(drugOrderIdText);
  const [detail, setDetail] = useState<DrugOrderDetail>();
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [notice, setNotice] = useState('');

  /** 读取物流详情；支付后的缓存已失效，因此优先读取服务端最新订单状态。 */
  async function loadLogistics() {
    if (!Number.isInteger(drugOrderId) || drugOrderId <= 0) {
      setNotice('订单编号不正确');
      setLoading(false);
      return;
    }
    setLoading(true);
    try {
      setDetail(await getDrugOrder(drugOrderId));
    } catch (error) {
      setNotice(getApiErrorMessage(error));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void loadLogistics(); }, [drugOrderId]);

  /** 仅在服务端订单进入待收货后允许确认，成功后刷新真实物流状态。 */
  async function receive() {
    if (!Number.isInteger(drugOrderId) || drugOrderId <= 0) return;
    setSubmitting(true);
    try {
      await confirmReceipt(drugOrderId, createIdempotencyKey());
      await loadLogistics();
    } catch (error) {
      setNotice(getApiErrorMessage(error));
      await loadLogistics();
    } finally {
      setSubmitting(false);
    }
  }

  const arrival = getDemoArrival(getSession()?.loginAt || new Date().toISOString());
  const logisticsText = getDrugOrderLogisticsText(detail);
  const traces = detail?.delivery?.traces || [];

  return <main className="subpage pharmacy-logistics-page"><PageHeader title="物流详情" backPath="/pharmacy" /><section className="subpage-content">
    {loading && <p className="empty-state">正在读取物流详情...</p>}
    {!loading && detail && <>
      {isPendingDrugOrder(detail.status) ? <section className="logistics-state-card"><Truck size={29} /><div><h2>订单待支付</h2><p>支付完成后将开始配送</p></div></section> : <section className="logistics-state-card"><Truck size={29} /><div><h2>{logisticsText}</h2><p>预计 {arrival} 送达</p></div></section>}
      <section className="logistics-order-summary"><h2>{detail.pharmacy?.name || detail.pharmacyName || '药房待确认'}</h2><p>订单金额：{formatAmount(detail.amountCent)}</p>{detail.items.map((item) => <article className="logistics-order-item" key={item.drugId}><b>{item.drugName}</b><span>{formatDrugOrderItemPrice(item.quantity, item.unitPriceCent)}</span></article>)}</section>
      {!isPendingDrugOrder(detail.status) && <section className="logistics-trace-card"><header><MapPin size={20} /><h2>配送轨迹</h2></header>{traces.length ? traces.map((trace) => <article key={`${trace.node}-${trace.occurredAt}`}><i /><div><b>{trace.node}</b><span>{new Date(trace.occurredAt).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })}</span></div></article>) : <p>配送服务已启动，等待物流节点更新</p>}</section>}
      {canConfirmDrugOrderReceipt(detail) && <button className="primary-button" disabled={submitting} type="button" onClick={() => void receive()}><PackageCheck size={19} />{submitting ? '确认中...' : '确认收货'}</button>}
    </>}
  </section>{notice && <div className="toast" role="status" onClick={() => setNotice('')}>{notice}</div>}</main>;
}
