import { useEffect, useRef, useState } from 'react';
import { ArrowLeft, Ellipsis, MapPin, Pencil, Plus, Search, Trash2, X } from 'lucide-react';
import { useNavigate } from 'umi';
import { Dialog } from '../../components/Dialog';
import { deleteDeliveryAddress, getDeliveryAddresses } from '../../services/delivery-address';
import type { DeliveryAddress } from '../../typings/api';
import { createIdempotencyKey, getApiErrorMessage } from '../../utils/form';

/** 展示当前账号地址簿，提供编辑、默认地址切换和删除操作。 */
export default function AddressesPage() {
  const navigate = useNavigate();
  const [addresses, setAddresses] = useState<DeliveryAddress[]>([]);
  const [keyword, setKeyword] = useState('');
  const [actionAddress, setActionAddress] = useState<DeliveryAddress>();
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

  /** 从三点菜单进入编辑页，并关闭当前地址的操作弹层。 */
  function editAddress() {
    if (!actionAddress) return;
    navigate(`/mine/addresses/${actionAddress.id}/edit`);
    setActionAddress(undefined);
  }

  /** 在三点菜单确认删除后关闭弹层，再执行原有的二次确认与软删除。 */
  async function deleteAddressFromMenu() {
    if (!actionAddress) return;
    const target = actionAddress;
    setActionAddress(undefined);
    await removeAddress(target);
  }

  const visibleAddresses = addresses.filter((address) => `${address.provinceName}${address.city}${address.district || ''}${address.detailAddress}${address.receiverName}${address.receiverPhone}`.includes(keyword.trim()));
  return <main className="subpage address-page"><header className="page-header address-page__header"><button className="icon-button" type="button" aria-label="返回我的" onClick={() => navigate('/mine')}><ArrowLeft size={22} /></button><h1>收货地址</h1><div><button className="text-button address-page__add" type="button" onClick={() => navigate('/mine/addresses/new')}><Plus size={17} />新增地址</button></div></header>
    <section className="subpage-content address-page__content"><div className="discovery-input address-search"><Search size={20} /><input aria-label="搜索收货地址" placeholder="搜索地址、收件人" value={keyword} onChange={(event) => setKeyword(event.target.value)} />{keyword && <button className="icon-button" aria-label="清空搜索内容" type="button" onClick={() => setKeyword('')}><X size={18} /></button>}</div>
      {loading && <p className="empty-state">正在读取收货地址...</p>}
      {!loading && visibleAddresses.map((address) => <article className="address-card" key={address.id}><div className="address-card__main"><p className="address-card__region">{address.provinceName}{address.city}{address.district || ''}</p><h2>{address.detailAddress}</h2><p className="address-card__receiver">{address.receiverName}　{address.receiverPhone}{address.isDefault && <em>默认</em>}</p></div><button className="icon-button address-card__menu" type="button" aria-label={`操作${address.detailAddress}`} onClick={() => setActionAddress(address)}><Ellipsis size={23} /></button></article>)}
      {!loading && !visibleAddresses.length && <div className="address-empty"><MapPin size={34} /><p>{keyword ? '未找到匹配的收货地址' : '暂无收货地址'}</p>{!keyword && <button className="primary-button" type="button" onClick={() => navigate('/mine/addresses/new')}>新增地址</button>}</div>}
    </section>{actionAddress && <Dialog title="地址操作" onClose={() => setActionAddress(undefined)}><div className="address-action-menu"><button type="button" onClick={editAddress}><Pencil size={18} />编辑</button><button className="danger" disabled={Boolean(operatingId)} type="button" onClick={() => void deleteAddressFromMenu()}><Trash2 size={18} />{operatingId === `delete:${actionAddress.id}` ? '删除中...' : '删除'}</button></div></Dialog>}{notice && <div className="toast" role="status" onClick={() => setNotice('')}>{notice}</div>}
  </main>;
}

/** 判断接口异常是否为需刷新服务端状态的冲突。 */
function isConflictError(error: unknown): boolean {
  return typeof error === 'object' && error !== null && 'status' in error && error.status === 409;
}
