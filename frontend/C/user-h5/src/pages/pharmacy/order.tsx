import { useEffect, useMemo, useState } from 'react';
import { ShoppingCart } from 'lucide-react';
import { useNavigate, useParams, useLocation } from 'umi';
import { Dialog } from '../../components/Dialog';
import { OrderDeliveryCard } from '../../components/OrderDeliveryCard';
import { PageHeader } from '../../components/PageHeader';
import { cancelDrugOrder, getDrugOrder } from '../../services/pharmacy';
import { simulatePayment } from '../../services/registration';
import type { DrugOrderDetail } from '../../typings/api';
import { resolveDrugOrderAgentReturnState } from '../../utils/agent-purchase';
import { createIdempotencyKey, getApiErrorMessage } from '../../utils/form';
import { formatAmount } from '../../utils/medical';
import { buildDrugOrderDetailPath, buildDrugOrderLogisticsPath, formatDrugOrderItemPrice, isPendingDrugOrder, resolveDrugOrderListPagePath, resolveDrugOrderPaymentId } from '../../utils/pharmacy-order';

/** 展示待支付购药订单，并通过确认购买弹窗完成支付或取消。 */
export default function DrugOrderPage() {
  const { drugOrderId: drugOrderIdText } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const drugOrderId = Number(drugOrderIdText);
  const query = useMemo(() => new URLSearchParams(location.search), [location.search]);
  const paymentIdFromUrl = Number(query.get('paymentId')) || undefined;
  const orderListPath = resolveDrugOrderListPagePath(query.get('returnTo'));
  const backPath = orderListPath || '/pharmacy';
  const currentOrderPath = buildDrugOrderDetailPath(drugOrderId, orderListPath);
  const returnToAgent = resolveDrugOrderAgentReturnState((location.state as { returnToAgent?: unknown } | null)?.returnToAgent);
  const [detail, setDetail] = useState<DrugOrderDetail>();
  const [password, setPassword] = useState('');
  const [paymentOpen, setPaymentOpen] = useState(false);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [notice, setNotice] = useState('');

  /** 从服务端读取订单详情；状态冲突后同样使用此函数恢复真实状态。 */
  async function loadOrder() {
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

  useEffect(() => { void loadOrder(); }, [drugOrderId]);

  /** 打开购买弹窗前再次确认订单仍待支付，避免过期订单继续输入密码。 */
  function openPaymentDialog() {
    if (!isPendingDrugOrder(detail?.status)) {
      if (detail) navigate(buildDrugOrderLogisticsPath(detail.id, currentOrderPath));
      return;
    }
    setPassword('');
    setPaymentOpen(true);
  }

  /** 在弹窗内调用模拟支付；AI 创建的订单成功后恢复原会话并通知配送信息。 */
  async function pay() {
    const paymentId = resolveDrugOrderPaymentId(detail, paymentIdFromUrl);
    if (!paymentId) {
      setNotice('当前订单暂未生成支付单');
      return;
    }
    if (!password.trim()) {
      setNotice('请输入登录密码');
      return;
    }
    setSubmitting(true);
    try {
      await simulatePayment(paymentId, password, createIdempotencyKey());
      setPaymentOpen(false);
      if (returnToAgent) {
        navigate('/agent', {
          replace: true,
          state: {
            from: returnToAgent.from,
            resumeSessionId: returnToAgent.sessionId,
            presetAction: { type: 'notify_drug_order_paid', drugOrderId },
          },
        });
        return;
      }
      navigate(buildDrugOrderLogisticsPath(drugOrderId, currentOrderPath));
    } catch (error) {
      setNotice(getApiErrorMessage(error));
      await loadOrder();
    } finally {
      setSubmitting(false);
    }
  }

  /** 取消入口仅保留在支付弹窗内，成功后回到当前订单来源页面释放订单占用。 */
  async function cancel() {
    if (!Number.isInteger(drugOrderId) || drugOrderId <= 0) return;
    setSubmitting(true);
    try {
      await cancelDrugOrder(drugOrderId, createIdempotencyKey());
      setPaymentOpen(false);
      navigate(backPath);
    } catch (error) {
      setNotice(getApiErrorMessage(error));
      await loadOrder();
    } finally {
      setSubmitting(false);
    }
  }

  const pendingPayment = isPendingDrugOrder(detail?.status);
  return <main className="subpage pharmacy-order-page"><PageHeader title="购药订单" backPath={backPath} showHome={false} /><section className="subpage-content">
    {loading && <p className="empty-state">正在读取订单...</p>}
    {!loading && detail && <><OrderDeliveryCard patientName={detail.patientName} patientPhone={detail.patientPhone} address={detail.delivery?.address} /><section className="pharmacy-order-summary"><h2>{detail.pharmacy?.name || detail.pharmacyName || '药房待确认'}</h2><p>订单金额：{formatAmount(detail.amountCent)}</p></section><section className="pharmacy-order-items">{detail.items.map((item) => <article key={item.drugId}><b>{item.drugName}</b><span>{formatDrugOrderItemPrice(item.quantity, item.unitPriceCent)}</span></article>)}</section>{pendingPayment ? <button className="primary-button" type="button" onClick={openPaymentDialog}><ShoppingCart size={19} />确认购买</button> : <button className="primary-button" type="button" onClick={openPaymentDialog}>查看物流详情</button>}</>}
  </section>{paymentOpen && <Dialog title="确认购买" onClose={() => !submitting && setPaymentOpen(false)}><div className="form-stack"><label>登录密码<input autoComplete="current-password" type="password" value={password} onChange={(event) => setPassword(event.target.value)} /></label><button className="primary-button" disabled={submitting} type="button" onClick={() => void pay()}>{submitting ? '购买中...' : '确认购买'}</button><button className="secondary-button" disabled={submitting} type="button" onClick={() => void cancel()}>取消订单</button></div></Dialog>}{notice && <div className="toast" role="status" onClick={() => setNotice('')}>{notice}</div>}</main>;
}
