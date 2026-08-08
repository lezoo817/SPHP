import { useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'umi';
import { PageHeader } from '../../components/PageHeader';
import { cancelAppointment, getPayment, simulatePayment } from '../../services/registration';
import { createIdempotencyKey, getApiErrorMessage } from '../../utils/form';
import { formatAmount, getRemainingSeconds } from '../../utils/medical';
import { isDuplicateDoctorAppointmentError, resolveAppointmentPaymentCancelPath } from '../../utils/registration';
import { resolveDrugOrderAgentReturnState } from '../../utils/agent-purchase';

/** 挂号支付单在页面展示所需的最小字段。 */
interface PaymentState {
  /** 支付单 ID。 */
  id: number;
  /** 支付金额，单位为分。 */
  amountCent: number;
  /** 服务端返回的支付状态。 */
  status: string;
  /** 待支付订单的失效时间。 */
  expireAt?: string;
}

/** 展示支付状态、待支付倒计时并执行模拟支付。 */
export default function PaymentPage() {
  const { paymentId } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const query = new URLSearchParams(location.search);
  const appointmentId = Number(query.get('appointmentId'));
  const cancelReturnPath = resolveAppointmentPaymentCancelPath(query.get('returnTo'));
  const returnToAgent = resolveDrugOrderAgentReturnState(
    (location.state as { returnToAgent?: unknown } | null)?.returnToAgent
  );
  const [payment, setPayment] = useState<PaymentState>();
  const [password, setPassword] = useState('');
  const [seconds, setSeconds] = useState(0);
  const [notice, setNotice] = useState('');
  const [isDuplicatePaymentBlocked, setDuplicatePaymentBlocked] = useState(false);
  const key = useRef<string>();

  /** 查询服务端支付单，支付完成后以服务端状态刷新页面。 */
  async function loadPayment() {
    try {
      const next = await getPayment(Number(paymentId));
      setPayment(next);
      setSeconds(getRemainingSeconds(next.expireAt));
    } catch (error) {
      setNotice(getApiErrorMessage(error));
    }
  }

  useEffect(() => {
    void loadPayment();
  }, [paymentId]);

  useEffect(() => {
    // 仅待支付订单展示并更新倒计时，成功后立即停止计时。
    if (payment?.status !== 'PENDING') return undefined;
    const timer = window.setInterval(() => setSeconds((value) => Math.max(0, value - 1)), 1000);
    return () => window.clearInterval(timer);
  }, [payment?.status]);

  /** 使用登录密码调用模拟支付，成功后立即恢复 AI 会话或返回首页。 */
  async function pay() {
    try {
      // 网络重试沿用首次生成的幂等键，避免重复支付。
      await simulatePayment(Number(paymentId), password, key.current || (key.current = createIdempotencyKey()));
      key.current = undefined;
      // 支付成功后立即跳转，无需用户手动点击"返回首页"（与购药流程一致）。
      returnHome();
    } catch (error) {
      if (isDuplicateDoctorAppointmentError(error)) {
        // 服务端已拒绝本笔重复支付，清除幂等键并保留取消订单入口释放号源。
        key.current = undefined;
        setDuplicatePaymentBlocked(true);
        setNotice('该账号已有同医生待就诊挂号，本笔订单不可继续支付');
        await loadPayment();
        return;
      }
      setNotice(getApiErrorMessage(error));
      // 状态冲突或支付失败后重新以服务端支付单为准。
      await loadPayment();
    }
  }

  /** 取消尚未支付的挂号订单，并按入口上下文返回对应页面。 */
  async function cancel() {
    try {
      await cancelAppointment(appointmentId, createIdempotencyKey());
      navigate(cancelReturnPath);
    } catch (error) {
      setNotice(getApiErrorMessage(error));
      await loadPayment();
    }
  }

  /** 支付成功后根据入口恢复 AI 会话或返回首页。 */
  function returnHome() {
    if (returnToAgent) {
      navigate('/agent', {
        replace: true,
        state: {
          from: returnToAgent.from,
          resumeSessionId: returnToAgent.sessionId,
          presetAction: { type: 'notify_appointment_paid', appointmentId },
        },
      });
      return;
    }
    navigate('/home');
  }

  const isPending = payment?.status === 'PENDING';
  const isSuccess = payment?.status === 'SUCCESS';
  const canPay = isPending && !isDuplicatePaymentBlocked;

  return <main className="subpage">
    <PageHeader title="挂号支付" showHome={false} />
    <section className="subpage-content payment-card">
      <h2>{isSuccess ? '支付成功' : '请完成支付'}</h2>
      <b>{formatAmount(payment?.amountCent || 0)}</b>
      {/* 仅待支付订单存在过期时间，支付成功后不再展示倒计时。 */}
      {isPending && <p>剩余支付时间：{String(Math.floor(seconds / 60)).padStart(2, '0')}:{String(seconds % 60).padStart(2, '0')}</p>}
      {isPending && <>
        <label>登录密码<input type="password" value={password} onChange={(event) => setPassword(event.target.value)} /></label>
        {isDuplicatePaymentBlocked && <p className="duplicate-payment-notice">该账号已有同医生待就诊挂号，请取消本笔订单释放号源。</p>}
        <button className="primary-button" type="button" disabled={!canPay} onClick={() => void pay()}>确认支付</button>
        <button className="secondary-button" type="button" onClick={() => void cancel()}>取消挂号</button>
      </>}
      {isSuccess && <button className="primary-button" type="button" onClick={returnHome}>返回首页</button>}
    </section>
    {notice && <div className="toast" onClick={() => setNotice('')}>{notice}</div>}
  </main>;
}
