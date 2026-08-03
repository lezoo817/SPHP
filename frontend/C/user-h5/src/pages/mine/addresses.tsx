import { useEffect, useRef, useState } from 'react';
import { ArrowLeft, MapPin, Pencil, Plus, Search, Star, Trash2, X } from 'lucide-react';
import { useNavigate } from 'umi';
import { deleteDeliveryAddress, getDeliveryAddresses, setDefaultDeliveryAddress } from '../../services/delivery-address';
import type { DeliveryAddress } from '../../typings/api';
import { createIdempotencyKey, getApiErrorMessage } from '../../utils/form';

/** 展示当前账号地址簿，提供编辑、默认地址切换和删除操作。 */
export default function AddressesPage() {
  const navigate = useNavigate();
  const [addresses, setAddresses] = useState<DeliveryAddress[]>([]);
  const [keyword, setKeyword] = useState('');
  const [manageMode, setManageMode] = useState(false);
  const [loading, setLoading] = useState(true);
  const [operatingId, setOperatingId] = useState('');
  const [notice, setNotice] = useState('');
  const operationKeys = useRef<Record<string, string>>({});

  /** 重新读取服务端默认地址优先的地址列表。 */
  async function loadAddresses() {
    setLoading(true);
    try {
      setAddresses(await getDeliveryAddresses());
    } catch (requestError) {
      setNotice(getApiErrorMessage(requestError));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void loadAddresses(); }, []);

  /** 设置一条非默认地址为默认地址，网络重试保留同一幂等键。 */
  async function makeDefault(address: DeliveryAddress) {
    const operationId = `default:${address.id}`;
    setOperatingId(operationId);
    const key = operationKeys.current[operationId] || (operationKeys.current[operationId] = createIdempotencyKey());
    try {
      await setDefaultDeliveryAddress(address.id, key);
      delete operationKeys.current[operationId];
      await loadAddresses();
    } catch (requestError) {
      // 默认地址冲突时刷新列表，避免继续基于旧默认状态操作。
      if (isConflictError(requestError)) {
        delete operationKeys.current[operationId];
        await loadAddresses();
      }
      setNotice(getApiErrorMessage(requestError));
    } finally {
      setOperatingId('');
    }
  }

  /** 二次确认后软删除当前账号拥有的地址。 */
  async function removeAddress(address: DeliveryAddress) {
    if (!window.confirm(`确认删除“${address.detailAddress}”吗？`)) return;
    const operationId = `delete:${address.id}`;
    setOperatingId(operationId);
    const key = operationKeys.current[operationId] || (operationKeys.current[operationId] = createIdempotencyKey());
    try {
      await deleteDeliveryAddress(address.id, key);
      delete operationKeys.current[operationId];
      await loadAddresses();
    } catch (requestError) {
      // 删除后端状态已变化时回读地址簿，避免本地继续展示已失效记录。
      if (isConflictError(requestError)) {
        delete operationKeys.current[operationId];
        await loadAddresses();
      }
      setNotice(getApiErrorMessage(requestError));
    } finally {
      setOperatingId('');
    }
  }

  const visibleAddresses = addresses.filter((address) => `${address.provinceName}${address.city}${address.district || ''}${address.detailAddress}${address.receiverName}${address.receiverPhone}`.includes(keyword.trim()));
  return <main className="subpage address-page"><header className="page-header address-page__header"><button className="icon-button" type="button" aria-label="返回我的" onClick={() => navigate('/mine')}><ArrowLeft size={22} /></button><h1>收货地址</h1><div><button className="text-button" type="button" onClick={() => setManageMode((value) => !value)}>{manageMode ? '完成' : '管理'}</button><button className="text-button address-page__add" type="button" onClick={() => navigate('/mine/addresses/new')}><Plus size={18} />新增</button></div></header>
    <section className="subpage-content address-page__content"><div className="discovery-input address-search"><Search size={20} /><input aria-label="搜索收货地址" placeholder="搜索地址、收件人" value={keyword} onChange={(event) => setKeyword(event.target.value)} />{keyword && <button className="icon-button" aria-label="清空搜索内容" type="button" onClick={() => setKeyword('')}><X size={18} /></button>}</div>
      {loading && <p className="empty-state">正在读取收货地址...</p>}
      {!loading && visibleAddresses.map((address) => <article className="address-card" key={address.id}><div className="address-card__main"><p className="address-card__region">{address.provinceName}{address.city}{address.district || ''}</p><h2>{address.detailAddress}</h2><p className="address-card__receiver">{address.receiverName}　{address.receiverPhone}{address.isDefault && <em>默认</em>}</p></div>{!manageMode && <button className="icon-button" type="button" aria-label="编辑收货地址" onClick={() => navigate(`/mine/addresses/${address.id}/edit`)}><Pencil size={22} /></button>}{manageMode && <div className="address-card__actions">{!address.isDefault && <button disabled={Boolean(operatingId)} type="button" onClick={() => void makeDefault(address)}><Star size={16} />{operatingId === `default:${address.id}` ? '设置中...' : '设为默认'}</button>}<button className="danger" disabled={Boolean(operatingId)} type="button" onClick={() => void removeAddress(address)}><Trash2 size={16} />{operatingId === `delete:${address.id}` ? '删除中...' : '删除'}</button></div>}</article>)}
      {!loading && !visibleAddresses.length && <div className="address-empty"><MapPin size={34} /><p>{keyword ? '未找到匹配的收货地址' : '暂无收货地址'}</p>{!keyword && <button className="primary-button" type="button" onClick={() => navigate('/mine/addresses/new')}>新增地址</button>}</div>}
    </section>{notice && <div className="toast" role="status" onClick={() => setNotice('')}>{notice}</div>}
  </main>;
}

/** 判断接口异常是否为需刷新服务端状态的冲突。 */
function isConflictError(error: unknown): boolean {
  return typeof error === 'object' && error !== null && 'status' in error && error.status === 409;
}
