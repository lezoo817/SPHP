import { useEffect, useMemo, useState } from 'react';
import { ShoppingCart, Truck } from 'lucide-react';
import { useLocation, useNavigate, useParams } from 'umi';
import { PageHeader } from '../../components/PageHeader';
import { PrescriptionPaper } from '../../components/PrescriptionPaper';
import { getPrescription } from '../../services/consultation';
import type { PrescriptionDetail } from '../../typings/api';
import { getApiErrorMessage } from '../../utils/form';
import { buildPharmacyInventoryPath, resolvePharmacyPatientId } from '../../utils/pharmacy';
import { buildDrugOrderLogisticsPath } from '../../utils/pharmacy-order';
import { getPrescriptionDisplayNumber } from '../../utils/prescription';

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

  return <main className="subpage pharmacy-prescription-detail-page"><PageHeader title="处方详情" backPath="/pharmacy" /><section className="subpage-content">
    {!patientId && <p className="form-error">请返回购药页重新选择就诊人</p>}
    {!detail && !notice && <p className="empty-state">正在读取处方详情...</p>}
    {detail && <PrescriptionPaper detail={detail} displayNumber={getPrescriptionDisplayNumber(detail.id, detail.issuedAt || issuedAtFromList)} issuedAt={issuedAtFromList} />}
  </section><footer className="pharmacy-purchase-bar"><button className="primary-button" type="button" disabled={!detail || (!patientId && !purchasedOrderId)} onClick={purchaseNow}>{purchasedOrderId ? <Truck size={19} /> : <ShoppingCart size={19} />}{purchasedOrderId ? '查看物流' : '立即购药'}</button></footer>{notice && <div className="toast" role="status" onClick={() => setNotice('')}>{notice}</div>}</main>;
}
