import { FormEvent, useCallback, useEffect, useMemo, useState } from 'react';
import { ArrowLeft, Search, X } from 'lucide-react';
import { useNavigate } from 'umi';
import { getDrugOrders } from '../../services/pharmacy';
import type { DrugOrder } from '../../typings/api';
import { formatAmount } from '../../utils/medical';
import { buildPharmacyHomePath, drugOrderTabs, getLogisticsStatusText, matchesDrugOrderTab, type DrugOrderTab } from '../../utils/pharmacy';
import { getApiErrorMessage } from '../../utils/form';

/** 将地址栏的 Tab 参数转换为受控物流分类。 */
function resolveTab(value: string | null): DrugOrderTab {
  return drugOrderTabs.some((item) => item.key === value) ? value as DrugOrderTab : 'ALL';
}

/** 展示指定就诊人的购药订单，并按名称和物流分类查询。 */
export default function PharmacyOrdersPage() {
  const nav = useNavigate();
  const params = useMemo(() => new URLSearchParams(location.search), []);
  const patientId = Number(params.get('patientId'));
  const [tab, setTab] = useState<DrugOrderTab>(resolveTab(params.get('tab')));
  const [keyword, setKeyword] = useState('');
  const [searchedKeyword, setSearchedKeyword] = useState('');
  const [orders, setOrders] = useState<DrugOrder[]>([]);
  const [loading, setLoading] = useState(false);
  const [notice, setNotice] = useState('');

  /** 根据当前患者和已提交关键词重新查询订单，后台刷新不打断页面浏览。 */
  const load = useCallback(async (silently = false) => {
    if (!Number.isInteger(patientId) || patientId <= 0) {
      if (!silently) setNotice('就诊人信息无效');
      return;
    }
    try {
      if (!silently) setLoading(true);
      const page = await getDrugOrders({ patientId, keyword: searchedKeyword, pageNo: 1, pageSize: 100 });
      setOrders(page.records);
    } catch (error) {
      if (!silently) setNotice(getApiErrorMessage(error));
    } finally {
      if (!silently) setLoading(false);
    }
  }, [patientId, searchedKeyword]);

  useEffect(() => {
    void load();
    // 物流状态由后端自动推进，订单页定时读取服务端最新状态。
    const timer = window.setInterval(() => { void load(true); }, 10_000);
    return () => window.clearInterval(timer);
  }, [load]);

  /** 提交订单名称模糊搜索。 */
  function search(event: FormEvent) {
    event.preventDefault();
    setSearchedKeyword(keyword.trim());
  }

  /** 清除关键词并恢复当前患者全部订单。 */
  function clear() {
    setKeyword('');
    setSearchedKeyword('');
  }

  const visibleOrders = orders.filter((order) => matchesDrugOrderTab(order, tab));
  return <main className="subpage discovery-page">
    <header className="page-header"><button className="icon-button" type="button" aria-label="返回购药" onClick={() => nav(buildPharmacyHomePath(patientId))}><ArrowLeft size={22} /></button><h1>我的订单</h1><span /></header>
    <section className="subpage-content pharmacy-orders">
      <form className="discovery-input order-search" onSubmit={search}>
        <Search size={20} /><input aria-label="搜索订单名称" value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="搜索订单名称" />
        {keyword && <button type="button" aria-label="清除搜索内容" onClick={clear}><X size={18} /></button>}<button className="search-submit" type="submit" disabled={loading}>搜索</button>
      </form>
      <nav className="order-tabs" aria-label="订单物流状态">{drugOrderTabs.map((item) => <button className={tab === item.key ? 'active' : ''} key={item.key} type="button" onClick={() => setTab(item.key)}>{item.label}</button>)}</nav>
      {loading && <p className="empty-state">订单加载中...</p>}
      {!loading && visibleOrders.map((order) => <button className="order-list-card" key={order.id} type="button" onClick={() => nav(`/pharmacy/order/${order.id}`)}>
        <div><b>{order.orderName || '药品订单'}</b><span>{order.pharmacyName}</span><small>就诊人：{order.patientName || '待确认'}</small></div>
        <aside><em>{getLogisticsStatusText(order.logisticsStatus)}</em><strong>{formatAmount(order.amountCent)}</strong></aside>
      </button>)}
      {!loading && !visibleOrders.length && <p className="empty-state">暂无符合条件的订单</p>}
    </section>
    {notice && <div className="toast" onClick={() => setNotice('')}>{notice}</div>}
  </main>;
}
