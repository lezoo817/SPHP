import { useEffect, useState } from 'react';
import { ShoppingCart, Sparkles } from 'lucide-react';
import { useLocation, useNavigate, useParams } from 'umi';
import { PageHeader } from '../../components/PageHeader';
import { PrescriptionPaper } from '../../components/PrescriptionPaper';
import { getPrescription } from '../../services/consultation';
import type { PrescriptionDetail } from '../../typings/api';
import { getApiErrorMessage } from '../../utils/form';
import { buildPharmacyInventoryPath, resolvePharmacyPatientId } from '../../utils/pharmacy';
import { buildMinePrescriptionListPath, buildPrescriptionInterpretationAgentState, getPrescriptionDisplayNumber } from '../../utils/prescription';

/** 展示已批准处方及药品用法。 */
export default function PrescriptionPage() {
  const { prescriptionId } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const [detail, setDetail] = useState<PrescriptionDetail>();
  const [notice, setNotice] = useState('');
  const query = new URLSearchParams(location.search);
  const patientId = resolvePharmacyPatientId(query.get('patientId'));
  const issuedAtFromList = query.get('issuedAt') || undefined;
  // 仅“我的处方”入口恢复其筛选条件，避免影响就诊助手既有详情返回行为。
  const backPath = query.get('source') === 'mine-prescriptions' ? buildMinePrescriptionListPath(query) : query.get('source') === 'assistant' ? '/assistant' : '/mine';

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

  /** 使用来源页面传入的本地就诊人进入真实库存页。 */
  function purchaseNow() {
    if (!patientId || !Number.isInteger(Number(prescriptionId))) {
      setNotice('请返回来源页面重新选择就诊人');
      return;
    }
    navigate(buildPharmacyInventoryPath(Number(prescriptionId), patientId, detail?.issuedAt || issuedAtFromList));
  }

  /** 创建独立 AI 会话并解读当前处方。 */
  function interpretWithAi() {
    const resolvedPrescriptionId = Number(prescriptionId);
    if (!detail || !Number.isInteger(resolvedPrescriptionId) || resolvedPrescriptionId <= 0) return;
    // 路由状态只传真实处方 ID，处方正文仍由 Agent 受控工具按当前账号读取。
    navigate('/agent', {
      state: buildPrescriptionInterpretationAgentState(`${location.pathname}${location.search}`, resolvedPrescriptionId),
    });
  }

  return <main className="subpage pharmacy-prescription-detail-page"><PageHeader title="处方详情" backPath={backPath} /><section className="subpage-content">{!patientId && <p className="form-error">请返回来源页面重新选择就诊人</p>}{!detail && !notice && <p className="empty-state">正在读取处方详情...</p>}{detail && <PrescriptionPaper detail={detail} displayNumber={getPrescriptionDisplayNumber(detail.id, detail.issuedAt || issuedAtFromList)} issuedAt={issuedAtFromList} />}</section><footer className="pharmacy-purchase-bar"><button className="pharmacy-ai-interpret-button" type="button" disabled={!detail || !Number.isInteger(Number(prescriptionId)) || Number(prescriptionId) <= 0} onClick={interpretWithAi}><Sparkles size={19} />AI一键解读</button><button className="primary-button" type="button" disabled={!detail || !patientId} onClick={purchaseNow}><ShoppingCart size={19} />立即购药</button></footer>{notice && <div className="toast" onClick={() => setNotice('')}>{notice}</div>}</main>;
}
