import { useEffect, useMemo, useState } from 'react';
import { ShoppingCart } from 'lucide-react';
import { useLocation, useNavigate, useParams } from 'umi';
import { PageHeader } from '../../components/PageHeader';
import { getPrescription } from '../../services/consultation';
import type { PrescriptionDetail } from '../../typings/api';
import { getApiErrorMessage } from '../../utils/form';
import { buildPharmacyInventoryPath, resolvePharmacyPatientId } from '../../utils/pharmacy';

/** 展示购药场景的已批准处方，并引导用户进入药房库存选择。 */
export default function PharmacyPrescriptionPage() {
  const { prescriptionId: prescriptionIdText } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const prescriptionId = Number(prescriptionIdText);
  const patientId = useMemo(() => resolvePharmacyPatientId(new URLSearchParams(location.search).get('patientId')), [location.search]);
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

  /** 进入库存页时显式透传购药页本地就诊人，避免使用其他页面的选择状态。 */
  function purchaseNow() {
    if (!patientId) {
      setNotice('请返回购药页重新选择就诊人');
      return;
    }
    navigate(buildPharmacyInventoryPath(prescriptionId, patientId));
  }

  return <main className="subpage pharmacy-prescription-detail-page"><PageHeader title="处方详情" backPath="/pharmacy" /><section className="subpage-content">
    {!patientId && <p className="form-error">请返回购药页重新选择就诊人</p>}
    {!detail && !notice && <p className="empty-state">正在读取处方详情...</p>}
    {detail && <><section className="pharmacy-prescription-heading"><h2>{detail.doctorName || '医生'}电子处方</h2><p>已批准处方可在附近有货药店购买。</p></section><section className="pharmacy-prescription-list">{detail.items.map((item) => <article className="record-card" key={item.drugId}><div><b>{item.drugName}{item.specification ? ` ${item.specification}` : ''}</b><span>{item.dosage || '用量待确认'} · {item.frequency || '频次待确认'}</span><small>{item.usage || '用法待确认'}{item.durationDays ? ` · ${item.durationDays}天` : ''}</small></div></article>)}</section></>}
  </section><footer className="pharmacy-purchase-bar"><button className="primary-button" type="button" disabled={!detail || !patientId} onClick={purchaseNow}><ShoppingCart size={19} />立即购药</button></footer>{notice && <div className="toast" role="status" onClick={() => setNotice('')}>{notice}</div>}</main>;
}
