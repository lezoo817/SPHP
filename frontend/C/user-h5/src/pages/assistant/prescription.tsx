import { useEffect, useState } from 'react';
import { useLocation, useParams } from 'umi';
import { PageHeader } from '../../components/PageHeader';
import { getPrescription } from '../../services/consultation';
import type { PrescriptionDetail } from '../../typings/api';
import { getApiErrorMessage } from '../../utils/form';
import { buildMinePrescriptionListPath } from '../../utils/prescription';

/** 展示已批准处方及药品用法。 */
export default function PrescriptionPage() {
  const { prescriptionId } = useParams();
  const location = useLocation();
  const [detail, setDetail] = useState<PrescriptionDetail>();
  const [notice, setNotice] = useState('');
  const query = new URLSearchParams(location.search);
  // 仅“我的处方”入口恢复其筛选条件，避免影响就诊助手既有详情返回行为。
  const backPath = query.get('source') === 'mine-prescriptions' ? buildMinePrescriptionListPath(query) : '/mine';

  /** 按路由处方编号读取已批准处方详情。 */
  async function loadPrescription() {
    try {
      const nextDetail = await getPrescription(Number(prescriptionId));
      setDetail(nextDetail);
    } catch (error) {
      setNotice(getApiErrorMessage(error));
    }
  }

  useEffect(() => { void loadPrescription(); }, [prescriptionId]);

  return <main className="subpage"><PageHeader title="处方详情" backPath={backPath} /><section className="subpage-content"><h2>{detail?.doctorName}医生处方</h2>{detail?.items.map((item) => <article className="record-card" key={item.drugId}><b>{item.drugName} {item.specification}</b><span>{item.dosage} · {item.frequency} · {item.usage}</span><em>{item.durationDays ? `${item.durationDays}天` : ''}</em></article>)}</section>{notice && <div className="toast" onClick={() => setNotice('')}>{notice}</div>}</main>;
}
