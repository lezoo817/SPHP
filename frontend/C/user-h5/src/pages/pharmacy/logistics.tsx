import { useCallback, useEffect, useState } from 'react';
import { MapPin, PackageCheck, Truck } from 'lucide-react';
import { useNavigate, useParams } from 'umi';
import { PageHeader } from '../../components/PageHeader';
import { confirmReceipt, getDrugOrder } from '../../services/pharmacy';
import type { DrugOrderDetail } from '../../typings/api';
import { createIdempotencyKey, getApiErrorMessage } from '../../utils/form';
import { formatAmount } from '../../utils/medical';
import { canConfirmDrugOrderReceipt, formatDrugOrderItemPrice, getDrugOrderExpectedDeliveryTime, getDrugOrderLogisticsSteps, getDrugOrderLogisticsText, isPendingDrugOrder, shouldPollDrugOrderLogistics } from '../../utils/pharmacy-order';

/** 展示支付完成后的购药配送状态、药品明细和确认收货操作。 */
export default function DrugOrderLogisticsPage() {
  const { drugOrderId: drugOrderIdText } = useParams();
  const navigate = useNavigate();
  const drugOrderId = Number(drugOrderIdText);
  const [detail, setDetail] = useState<DrugOrderDetail>();
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [notice, setNotice] = useState('');

  /** 读取物流详情；后台轮询仅更新数据，避免反复显示加载态或错误提示。 */
  const loadLogistics = useCallback(async (silently = false) => {
    if (!Number.isInteger(drugOrderId) || drugOrderId <= 0) {
      if (!silently) {
        setNotice('订单编号不正确');
        setLoading(false);
      }
      return;
    }
    if (!silently) setLoading(true);
    try {
      setDetail(await getDrugOrder(drugOrderId));
    } catch (error) {
      if (!silently) setNotice(getApiErrorMessage(error));
    } finally {
      if (!silently) setLoading(false);
    }
  }, [drugOrderId]);

  useEffect(() => { void loadLogistics(); }, [loadLogistics]);

  useEffect(() => {
    if (!shouldPollDrugOrderLogistics(detail)) return undefined;
    // 后端每 30 秒推进一次物流，本页每 15 秒读取一次以更新当前阶段。
    const timer = window.setInterval(() => { void loadLogistics(true); }, 15_000);
    return () => window.clearInterval(timer);
  }, [detail, loadLogistics]);

  /** 仅在服务端订单进入待收货后允许确认，成功后刷新真实物流状态。 */
  async function receive() {
    if (!Number.isInteger(drugOrderId) || drugOrderId <= 0) return;
    setSubmitting(true);
    try {
      await confirmReceipt(drugOrderId, createIdempotencyKey());
      await loadLogistics(true);
    } catch (error) {
      setNotice(getApiErrorMessage(error));
      await loadLogistics(true);
    } finally {
      setSubmitting(false);
    }
  }

  const arrival = getDrugOrderExpectedDeliveryTime(detail);
  const logisticsText = getDrugOrderLogisticsText(detail);
  const logisticsStatus = detail?.delivery?.logisticsStatus || detail?.logisticsStatus;
  const steps = getDrugOrderLogisticsSteps(logisticsStatus);

  return <main className="subpage pharmacy-logistics-page"><PageHeader title="物流详情" backPath="/pharmacy" /><section className="subpage-content">
    {loading && <p className="empty-state">正在读取物流详情...</p>}
    {!loading && detail && <>
      {isPendingDrugOrder(detail.status) ? <section className="logistics-state-card"><Truck size={29} /><div><h2>订单待支付</h2><p>支付完成后将开始配送</p></div></section> : <section className="logistics-state-card"><Truck size={29} /><div><h2>{logisticsText}</h2><p>预计 {arrival} 送达</p></div></section>}
      <section className="logistics-order-summary"><h2>{detail.pharmacy?.name || detail.pharmacyName || '药房待确认'}</h2><p>订单金额：{formatAmount(detail.amountCent)}</p>{detail.items.map((item) => <article className="logistics-order-item" key={item.drugId}><b>{item.drugName}</b><span>{formatDrugOrderItemPrice(item.quantity, item.unitPriceCent)}</span></article>)}</section>
      {!isPendingDrugOrder(detail.status) && <section className="logistics-trace-card"><header><MapPin size={20} /><h2>配送轨迹</h2></header><ol className="logistics-progress">{steps.map((step) => <li className={`logistics-progress__step is-${step.state}`} key={step.label}><i className="logistics-progress__dot" aria-hidden="true" /><b>{step.label}</b></li>)}</ol></section>}
      {canConfirmDrugOrderReceipt(detail) && <button className="primary-button" disabled={submitting} type="button" onClick={() => void receive()}><PackageCheck size={19} />{submitting ? '确认中...' : '确认收货'}</button>}
    </>}
  </section>{notice && <div className="toast" role="status" onClick={() => setNotice('')}>{notice}</div>}</main>;
}
