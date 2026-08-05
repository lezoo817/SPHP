import { useEffect, useMemo, useState } from 'react';
import { MapPin } from 'lucide-react';
import { useLocation, useNavigate, useParams } from 'umi';
import { PageHeader } from '../../components/PageHeader';
import { getDeliveryAddresses } from '../../services/delivery-address';
import { createDrugOrder, getPharmacyRecommendations } from '../../services/pharmacy';
import type { DeliveryAddress, DeliveryPharmacyRecommendation } from '../../typings/api';
import { createIdempotencyKey, getApiErrorMessage } from '../../utils/form';
import { formatAmount } from '../../utils/medical';
import { buildPharmacyPrescriptionPath, resolvePharmacyPatientId } from '../../utils/pharmacy';

/** 展示收货地址下可配送的处方药房，并从推荐结果创建购药订单。 */
export default function PharmacyPrescriptionInventoryPage() {
  const { prescriptionId: prescriptionIdText } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const prescriptionId = Number(prescriptionIdText);
  const patientId = useMemo(() => resolvePharmacyPatientId(new URLSearchParams(location.search).get('patientId')), [location.search]);
  const issuedAt = new URLSearchParams(location.search).get('issuedAt') || undefined;
  const [addresses, setAddresses] = useState<DeliveryAddress[]>([]);
  const [selectedAddressId, setSelectedAddressId] = useState<number>();
  const [items, setItems] = useState<DeliveryPharmacyRecommendation[]>([]);
  const [loadingAddresses, setLoadingAddresses] = useState(true);
  const [loadingRecommendations, setLoadingRecommendations] = useState(false);
  const [notice, setNotice] = useState('');

  /** 读取当前账号的地址簿，并默认选择服务端标记的默认地址。 */
  async function loadAddresses() {
    setLoadingAddresses(true);
    try {
      const nextAddresses = await getDeliveryAddresses();
      setAddresses(nextAddresses);
      // 地址变更后优先保留用户刚刚选择的地址，否则使用默认地址。
      setSelectedAddressId((current) => nextAddresses.some((address) => address.id === current)
        ? current : nextAddresses.find((address) => address.isDefault)?.id || nextAddresses[0]?.id);
    } catch (error) {
      setNotice(getApiErrorMessage(error));
    } finally {
      setLoadingAddresses(false);
    }
  }

  /** 使用已选地址读取后端计算的距离、时效、价格和库存推荐。 */
  async function loadRecommendations(addressId: number) {
    if (!patientId) {
      setNotice('请返回购药页重新选择就诊人');
      return;
    }
    if (!Number.isInteger(prescriptionId) || prescriptionId <= 0) {
      setNotice('处方编号不正确');
      return;
    }
    setLoadingRecommendations(true);
    try {
      const next = await getPharmacyRecommendations(patientId, prescriptionId, addressId);
      setItems(next);
    } catch (error) {
      setNotice(getApiErrorMessage(error));
    } finally {
      setLoadingRecommendations(false);
    }
  }

  useEffect(() => { void loadAddresses(); }, []);
  useEffect(() => {
    if (!selectedAddressId) {
      setItems([]);
      return;
    }
    void loadRecommendations(selectedAddressId);
  }, [patientId, prescriptionId, selectedAddressId]);

  /** 选择推荐药房后以地址簿 ID 创建订单，后端固化不可变地址快照。 */
  async function order(pharmacy: DeliveryPharmacyRecommendation) {
    if (!patientId || !selectedAddressId) return;
    try {
      const result = await createDrugOrder({ patientId, prescriptionId, pharmacyId: pharmacy.pharmacyId, addressId: selectedAddressId }, createIdempotencyKey());
      navigate(`/pharmacy/order/${result.drugOrderId}?paymentId=${result.paymentId}`);
    } catch (error) {
      setNotice(getApiErrorMessage(error));
    }
  }

  const backPath = patientId && Number.isInteger(prescriptionId) ? buildPharmacyPrescriptionPath(prescriptionId, patientId, issuedAt) : '/pharmacy';
  const selectedAddress = addresses.find((address) => address.id === selectedAddressId);
  const isLoading = loadingAddresses || loadingRecommendations;

  return <main className="subpage"><PageHeader title="附近有货药店" backPath={backPath} /><section className="subpage-content">
    <section className="pharmacy-delivery-address" aria-label="收货地址选择"><MapPin size={20} /><div><b>配送至</b>{selectedAddress ? <span>{selectedAddress.provinceName}{selectedAddress.city}{selectedAddress.district || ''}{selectedAddress.detailAddress}</span> : <span>请先添加收货地址</span>}</div>{addresses.length > 0 ? <select aria-label="选择收货地址" value={selectedAddressId || ''} onChange={(event) => setSelectedAddressId(Number(event.target.value))}>{addresses.map((address) => <option key={address.id} value={address.id}>{address.receiverName} · {address.provinceName}{address.city}{address.detailAddress}</option>)}</select> : <button className="text-button" type="button" onClick={() => navigate('/mine/addresses/new')}>新增</button>}</section>
    {selectedAddress && <p className="result-count">已按所选地址计算药房距离与配送时效</p>}
    {isLoading && <p className="empty-state">正在读取可配送药房...</p>}
    {!isLoading && selectedAddress && items.map((pharmacy) => <button className="record-card pharmacy-recommendation-card" key={pharmacy.pharmacyId} type="button" onClick={() => void order(pharmacy)}><div><b>{pharmacy.name}{pharmacy.isDefault && <em>默认药房</em>}</b><span>{formatDistance(pharmacy.distanceMeters)} · 预计 {formatDeliveryDuration(pharmacy.estimatedDeliveryMinutes)}</span><small>{pharmacy.recommendReasons.join(' · ')}</small></div><aside><strong>{formatAmount(pharmacy.totalAmountCent)}</strong><small>{pharmacy.items.length} 种药均有货</small></aside></button>)}
    {!isLoading && selectedAddress && !items.length && <p className="empty-state">所选地址暂无可购买药店</p>}
    {!isLoading && !selectedAddress && <p className="empty-state">请先新增并选择收货地址</p>}
  </section>{notice && <div className="toast" role="status" onClick={() => setNotice('')}>{notice}</div>}</main>;
}

/** 将后端距离米数转换为药房列表展示文案。 */
function formatDistance(distanceMeters: number): string {
  return distanceMeters >= 1_000 ? `${(distanceMeters / 1_000).toFixed(1)}km` : `${distanceMeters}m`;
}

/** 将后端预计配送分钟数转换为小时和分钟展示文案。 */
function formatDeliveryDuration(minutes: number): string {
  const hours = Math.floor(minutes / 60);
  const remainingMinutes = minutes % 60;
  return remainingMinutes ? `${hours}小时${remainingMinutes}分钟` : `${hours}小时`;
}
