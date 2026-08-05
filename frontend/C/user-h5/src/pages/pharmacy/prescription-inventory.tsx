import { useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'umi';
import { PageHeader } from '../../components/PageHeader';
import { createDrugOrder, getInventory } from '../../services/pharmacy';
import type { PharmacyInventory } from '../../typings/api';
import { createIdempotencyKey, getApiErrorMessage } from '../../utils/form';
import { formatAmount } from '../../utils/medical';
import { buildPharmacyPrescriptionPath, resolvePharmacyPatientId } from '../../utils/pharmacy';

/** 展示处方对应的附近有货药店，并从选中药房创建购药订单。 */
export default function PharmacyPrescriptionInventoryPage() {
  const { prescriptionId: prescriptionIdText } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const prescriptionId = Number(prescriptionIdText);
  const patientId = useMemo(() => resolvePharmacyPatientId(new URLSearchParams(location.search).get('patientId')), [location.search]);
  const issuedAt = new URLSearchParams(location.search).get('issuedAt') || undefined;
  const [items, setItems] = useState<PharmacyInventory[]>([]);
  const [loading, setLoading] = useState(true);
  const [notice, setNotice] = useState('');

  /** 使用详情页透传的就诊人读取处方库存，缺失参数时不发送患者范围请求。 */
  async function loadInventory() {
    if (!patientId) {
      setNotice('请返回购药页重新选择就诊人');
      setLoading(false);
      return;
    }
    if (!Number.isInteger(prescriptionId) || prescriptionId <= 0) {
      setNotice('处方编号不正确');
      setLoading(false);
      return;
    }
    setLoading(true);
    try {
      const next = await getInventory(patientId, prescriptionId);
      setItems(next);
    } catch (error) {
      setNotice(getApiErrorMessage(error));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void loadInventory(); }, [patientId, prescriptionId]);

  /** 选择药房后创建订单；所有写操作使用新的 UUID 幂等键。 */
  async function order(pharmacy: PharmacyInventory) {
    if (!patientId) return;
    try {
      const result = await createDrugOrder({ patientId, prescriptionId, pharmacyId: pharmacy.pharmacyId, deliveryAddress: '河南省郑州市演示收货地址' }, createIdempotencyKey());
      navigate(`/pharmacy/order/${result.drugOrderId}?paymentId=${result.paymentId}`);
    } catch (error) {
      setNotice(getApiErrorMessage(error));
    }
  }

  const backPath = patientId && Number.isInteger(prescriptionId) ? buildPharmacyPrescriptionPath(prescriptionId, patientId, issuedAt) : '/pharmacy';

  return <main className="subpage"><PageHeader title="附近有货药店" backPath={backPath} /><section className="subpage-content">
    <p className="result-count">郑州市 · 1.2km 内</p>
    {loading && <p className="empty-state">正在读取附近药店...</p>}
    {!loading && items.map((pharmacy, index) => <button className="record-card" key={pharmacy.pharmacyId} type="button" onClick={() => void order(pharmacy)}><div><b>{pharmacy.name}</b><span>{pharmacy.items.filter((item) => item.availableCount > 0).length}种药均有货 · {(index ? 980 : 350)}m</span></div><em>{formatAmount(pharmacy.items.reduce((sum, item) => sum + item.unitPriceCent, 0))}</em></button>)}
    {!loading && !items.length && <p className="empty-state">附近暂无可购买药店</p>}
  </section>{notice && <div className="toast" role="status" onClick={() => setNotice('')}>{notice}</div>}</main>;
}
