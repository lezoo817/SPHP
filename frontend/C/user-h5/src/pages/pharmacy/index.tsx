import { useEffect, useState } from 'react';
import { Package, RefreshCw, Truck } from 'lucide-react';
import { useNavigate } from 'umi';
import { BottomTab } from '../../components/BottomTab';
import { Dialog } from '../../components/Dialog';
import { resolveSelfPatientId } from '../../models/selection';
import { getFamilyMembers } from '../../services/family';
import { getPrescriptions } from '../../services/consultation';
import { getDrugOrders } from '../../services/pharmacy';
import type { DrugOrder, FamilyMember, Prescription } from '../../typings/api';
import { drugOrderTabs, matchesDrugOrderTab, type DrugOrderTab } from '../../utils/pharmacy';

/** 展示本人默认的处方和四类物流入口，并支持本页切换家人。 */
export default function PharmacyPage() {
  const nav = useNavigate();
  const [members, setMembers] = useState<FamilyMember[]>([]);
  const [patientId, setPatientId] = useState<number>();
  const [prescriptions, setPrescriptions] = useState<Prescription[]>([]);
  const [orders, setOrders] = useState<DrugOrder[]>([]);
  const [open, setOpen] = useState(false);
  const [notice, setNotice] = useState('');
  const current = members.find((member) => member.patientId === patientId);

  /** 加载当前页面选择就诊人的处方及订单摘要。 */
  async function load() {
    try {
      const next = await getFamilyMembers();
      setMembers(next);
      const target = patientId || resolveSelfPatientId(next);
      if (!target) return;
      if (!patientId) setPatientId(target);
      // 首页统计需要读取当前患者全部订单，避免分页造成状态数量失真。
      const [prescriptionPage, orderPage] = await Promise.all([
        getPrescriptions(target),
        getDrugOrders({ patientId: target, pageSize: 100 }),
      ]);
      setPrescriptions(prescriptionPage.records);
      setOrders(orderPage.records);
    } catch (error: any) {
      setNotice(error.message || '购药数据加载失败');
    }
  }

  useEffect(() => { void load(); }, [patientId]);

  /** 打开保留当前就诊人的订单列表分类。 */
  function openOrders(tab: DrugOrderTab) {
    if (!patientId) {
      setNotice('暂未获取到就诊人信息');
      return;
    }
    nav(`/pharmacy/orders?patientId=${patientId}&tab=${tab}`);
  }

  return <main className="assistant-page">
    <header className="assistant-title"><h1>购药</h1></header>
    <section className="assistant-content">
      <button className="assistant-patient" type="button" onClick={() => setOpen(true)}>
        就诊人 <b>{current?.name || '未选择'}</b><span>{current?.phone || ''}</span><b>切换 <RefreshCw size={18} /></b>
      </button>
      <h2>我的处方</h2>
      {prescriptions.map((prescription) => <button className="record-card" key={prescription.id} type="button" onClick={() => nav(`/pharmacy/prescription/${prescription.id}`)}>
        <Package size={25} /><div><b>{prescription.doctorName}电子处方</b><span>已批准 · {prescription.issuedAt}</span></div><em>待购药</em>
      </button>)}
      {!prescriptions.length && <p className="empty-state">暂无可购药处方</p>}
      <h2>我的物流</h2>
      <section className="logistics-status-grid">
        {drugOrderTabs.map((tab) => <button className="logistics-status-card" key={tab.key} type="button" onClick={() => openOrders(tab.key)}>
          <b>{orders.filter((order) => matchesDrugOrderTab(order, tab.key)).length}</b><span>{tab.label}</span><Truck size={18} />
        </button>)}
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
