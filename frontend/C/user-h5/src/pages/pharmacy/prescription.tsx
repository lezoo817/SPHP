import { useEffect, useMemo, useState } from 'react';
import { ShoppingCart, Sparkles, Truck } from 'lucide-react';
import { useLocation, useNavigate, useParams } from 'umi';
import { PageHeader } from '../../components/PageHeader';
import { PrescriptionPaper } from '../../components/PrescriptionPaper';
import { getFamilyMembers } from '../../services/family';
import { getPrescription } from '../../services/consultation';
import type { PrescriptionDetail } from '../../typings/api';
import { getApiErrorMessage } from '../../utils/form';
import { buildPharmacyHomePath, buildPharmacyInventoryPath, resolvePharmacyPatientId } from '../../utils/pharmacy';
import { buildDrugOrderLogisticsPath } from '../../utils/pharmacy-order';
import { buildPrescriptionInterpretationAgentState, getPrescriptionDisplayNumber } from '../../utils/prescription';

/** 展示购药场景的已批准处方，并按购买状态进入药房库存或物流详情。 */
export default function PharmacyPrescriptionPage() {
  const { prescriptionId: prescriptionIdText } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const prescriptionId = Number(prescriptionIdText);
  const params = useMemo(() => new URLSearchParams(location.search), [location.search]);
  const patientId = useMemo(() => resolvePharmacyPatientId(params.get('patientId')), [params]);
  const issuedAtFromList = params.get('issuedAt') || undefined;
  const purchasedOrderId = useMemo(() => {
    const value = Number(params.get('drugOrderId'));
    return Number.isInteger(value) && value > 0 ? value : undefined;
  }, [params]);
  const [detail, setDetail] = useState<PrescriptionDetail>();
  const [patientName, setPatientName] = useState('当前就诊人');
  const [notice, setNotice] = useState('');

  /** 读取处方正文；处方详情由服务端按当前账号校验可见性。 */
  async function loadPrescription() {
    if (!Number.isInteger(prescriptionId) || prescriptionId <= 0) {
      setNotice('处方编号不正确');
      return;
    }
    try {
      const next = await getPrescription(prescriptionId);
      setDetail(next);
    } catch (error) {
      setNotice(getApiErrorMessage(error));
    }
  }

  useEffect(() => { void loadPrescription(); }, [prescriptionId]);
  useEffect(() => {
    if (!patientId) return;
    void getFamilyMembers().then((members) => setPatientName(members.find((member) => member.patientId === patientId)?.name || '当前就诊人')).catch(() => undefined);
  }, [patientId]);

  /** 未购买时进入库存页；已购买时直接查看关联订单物流。 */
  function purchaseNow() {
    if (purchasedOrderId) {
      // 已购买处方直接进入其订单物流，避免再次创建同一处方订单。
      navigate(buildDrugOrderLogisticsPath(purchasedOrderId));
      return;
    }
    if (!patientId) {
      setNotice('请返回购药页重新选择就诊人');
      return;
    }
    navigate(buildPharmacyInventoryPath(prescriptionId, patientId, detail?.issuedAt || issuedAtFromList));
  }

  /** 创建独立 AI 会话并解读当前处方。 */
  function interpretWithAi() {
    if (!detail || !Number.isInteger(prescriptionId) || prescriptionId <= 0) return;
    // 路由状态只传真实处方 ID，处方正文仍由 Agent 受控工具按当前账号读取。
    navigate('/agent', {
      state: buildPrescriptionInterpretationAgentState(`${location.pathname}${location.search}`, prescriptionId),
    });
  }

  return <main className="subpage pharmacy-prescription-detail-page"><PageHeader title="处方详情" backPath={buildPharmacyHomePath(patientId)} /><section className="subpage-content">
    {!patientId && <p className="form-error">请返回购药页重新选择就诊人</p>}
    {!detail && !notice && <p className="empty-state">正在读取处方详情...</p>}
    {detail && <PrescriptionPaper detail={detail} patientName={patientName} displayNumber={getPrescriptionDisplayNumber(detail.id, detail.issuedAt || issuedAtFromList)} issuedAt={issuedAtFromList} />}
  </section><footer className="pharmacy-purchase-bar"><button className="pharmacy-ai-interpret-button" type="button" disabled={!detail || !Number.isInteger(prescriptionId) || prescriptionId <= 0} onClick={interpretWithAi}><Sparkles size={19} />AI一键解读</button><button className="primary-button" type="button" disabled={!detail || (!patientId && !purchasedOrderId)} onClick={purchaseNow}>{purchasedOrderId ? <Truck size={19} /> : <ShoppingCart size={19} />}{purchasedOrderId ? '查看物流' : '立即购药'}</button></footer>{notice && <div className="toast" role="status" onClick={() => setNotice('')}>{notice}</div>}</main>;
}
