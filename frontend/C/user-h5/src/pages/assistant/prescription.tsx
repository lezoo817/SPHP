import { useEffect, useState } from 'react';
import { useParams } from 'umi';
import { PageHeader } from '../../components/PageHeader';
import { getPrescription } from '../../services/consultation';
import type { PrescriptionDetail } from '../../typings/api';
import { getApiErrorMessage } from '../../utils/form';

/** 展示已批准处方及药品用法。 */
export default function PrescriptionPage() { const { prescriptionId } = useParams(); const [detail, setDetail] = useState<PrescriptionDetail>(); const [notice, setNotice] = useState(''); useEffect(() => { void getPrescription(Number(prescriptionId)).then(setDetail).catch((error) => setNotice(getApiErrorMessage(error))); }, [prescriptionId]); return <main className="subpage"><PageHeader title="处方详情" /><section className="subpage-content"><h2>{detail?.doctorName}医生处方</h2>{detail?.items.map((item) => <article className="record-card" key={item.drugId}><b>{item.drugName} {item.specification}</b><span>{item.dosage} · {item.frequency} · {item.usage}</span><em>{item.durationDays ? `${item.durationDays}天` : ''}</em></article>)}</section>{notice && <div className="toast" onClick={() => setNotice('')}>{notice}</div>}</main>; }
