import { useEffect, useState } from 'react';
import { ChevronRight, CircleX, ClipboardList, Package, PackageCheck, PackageOpen, RefreshCw, Truck } from 'lucide-react';
import { useLocation, useNavigate } from 'umi';
import { BottomTab } from '../../components/BottomTab';
import { Dialog } from '../../components/Dialog';
import { resolveSelfPatientId } from '../../models/selection';
import { getFamilyMembers } from '../../services/family';
import { getPrescriptions } from '../../services/consultation';
import { getDrugOrders } from '../../services/pharmacy';
import type { DrugOrder, FamilyMember, Prescription } from '../../typings/api';
import { buildPharmacyPrescriptionPath, drugOrderTabs, resolvePharmacyPatientId, type DrugOrderTab } from '../../utils/pharmacy';
import { findPurchasedDrugOrder } from '../../utils/pharmacy-order';
import { formatPrescriptionIssuedAt } from '../../utils/prescription';

/** 展示本人默认的处方和五类订单入口，并支持本页切换家人。 */
export default function PharmacyPage() {
  const nav = useNavigate();
  const location = useLocation();
  const patientIdFromUrl = resolvePharmacyPatientId(new URLSearchParams(location.search).get('patientId'));
  const [members, setMembers] = useState<FamilyMember[]>([]);
  const [patientId, setPatientId] = useState<number>();
  const [prescriptions, setPrescriptions] = useState<Prescription[]>([]);
  const [orders, setOrders] = useState<DrugOrder[]>([]);
  const [open, setOpen] = useState(false);
  const [notice, setNotice] = useState('');
  const current = members.find((member) => member.patientId === patientId);

  /** 加载当前页面选择就诊人的处方。 */
  async function load() {
    try {
      const next = await getFamilyMembers();
      setMembers(next);
      // 处方详情返回时优先恢复购药模块此前选择的就诊人，不影响其他页面选择。
      const target = patientId || (next.some((member) => member.patientId === patientIdFromUrl) ? patientIdFromUrl : undefined) || resolveSelfPatientId(next);
      if (!target) return;
      if (!patientId) setPatientId(target);
      // 处方和订单并行读取，确保支付完成后首页能立即切换为已购买。
      const [prescriptionPage, orderPage] = await Promise.all([
        getPrescriptions({ patientId: target }),
        getDrugOrders({ patientId: target, pageNo: 1, pageSize: 100 }),
      ]);
      setPrescriptions(prescriptionPage.records);
      setOrders(orderPage.records);
    } catch (error: any) {
      setNotice(error.message || '购药数据加载失败');
    }
  }

  useEffect(() => { void load(); }, [patientId, patientIdFromUrl]);

  /** 打开保留当前就诊人的订单列表分类。 */
  function openOrders(tab: DrugOrderTab) {
    if (!patientId) {
      setNotice('暂未获取到就诊人信息');
      return;
    }
    nav(`/pharmacy/orders?patientId=${patientId}&tab=${tab}`);
  }

  /** 跳转到“我的”处方查询页，并将购药页当前就诊人作为初始选择。 */
  function openMinePrescriptions() {
    if (!patientId) {
      setNotice('暂未获取到就诊人信息');
      return;
    }
    nav(`/mine/prescriptions?patientId=${patientId}`);
  }

  return <main className="assistant-page">
    <header className="assistant-title"><h1>购药</h1></header>
    <section className="assistant-content">
      <button className="assistant-patient" type="button" onClick={() => setOpen(true)}>
        就诊人 <b>{current?.name || '未选择'}</b><span>{current?.phone || ''}</span><b>切换 <RefreshCw size={18} /></b>
      </button>
      <button className="pharmacy-section-link" type="button" onClick={openMinePrescriptions}><span>我的处方</span><ChevronRight size={22} /></button>
      {prescriptions.map((prescription) => {
        const purchasedOrder = findPurchasedDrugOrder(orders, prescription.id);
        return <button className="record-card" key={prescription.id} type="button" onClick={() => patientId && nav(buildPharmacyPrescriptionPath(prescription.id, patientId, prescription.issuedAt, purchasedOrder?.id))}>
          <Package size={25} /><div className="pharmacy-prescription-summary"><b>{prescription.doctorName}电子处方</b><span>已批准</span><small>开具时间：{formatPrescriptionIssuedAt(prescription.issuedAt)}</small></div><em>{purchasedOrder ? '已购买' : '待购药'}</em>
        </button>;
      })}
      {!prescriptions.length && <p className="empty-state">暂无可购药处方</p>}
      <section className="logistics-card">
        <h2>我的物流</h2>
        <div className="logistics-status-grid">
        {drugOrderTabs.map((tab) => <button className="logistics-status-card" key={tab.key} type="button" onClick={() => openOrders(tab.key)}>
          {tab.key === 'ALL' && <ClipboardList size={27} />}
          {tab.key === 'TRANSIT' && <Truck size={27} />}
          {tab.key === 'TO_RECEIVE' && <PackageOpen size={27} />}
          {tab.key === 'RECEIVED' && <PackageCheck size={27} />}
          {tab.key === 'INVALID' && <CircleX size={27} />}
          <span>{tab.label}</span>
        </button>)}
        </div>
      </section>
    </section>
    {open && <Dialog title="切换就诊人" onClose={() => setOpen(false)}>
      {members.map((member) => <button className="choice-row" key={member.patientId} type="button" onClick={() => { setPatientId(member.patientId); setOpen(false); }}>{member.name}<small>{member.relationName || member.relation}</small></button>)}
      {members.filter((member) => member.relation !== 'SELF').length === 0 && <p className="empty-state">当前用户未绑定亲属</p>}
    </Dialog>}
    {notice && <div className="toast" onClick={() => setNotice('')}>{notice}</div>}
    <BottomTab onUnavailable={() => setNotice('该页面暂未开放')} />
  </main>;
}
