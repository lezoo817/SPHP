import { useEffect, useRef, useState } from 'react';
import { ArrowLeft, Ellipsis, House, MapPin, Pencil, Plus, Search, Trash2, X } from 'lucide-react';
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
  const [deleteAddress, setDeleteAddress] = useState<DeliveryAddress>();
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

  /** 从三点菜单打开删除确认弹窗，避免使用浏览器原生确认框。 */
  function requestDeleteAddress() {
    if (!actionAddress) return;
    setDeleteAddress(actionAddress);
    setActionAddress(undefined);
  }

  /** 确认删除后调用服务端软删除接口，并保持失败时的重试幂等键。 */
  async function confirmDeleteAddress() {
    if (!deleteAddress) return;
    const target = deleteAddress;
    setDeleteAddress(undefined);
    await removeAddress(target);
  }

  const visibleAddresses = addresses.filter((address) => `${address.provinceName}${address.city}${address.district || ''}${address.detailAddress}${address.receiverName}${address.receiverPhone}`.includes(keyword.trim()));
  return <main className="subpage address-page"><header className="page-header address-page__header"><div className="page-header__controls"><button className="icon-button" type="button" aria-label="返回我的" onClick={() => navigate('/mine')}><ArrowLeft size={22} /></button><button className="page-header__home icon-button" type="button" aria-label="返回首页" onClick={() => navigate('/home')}><House size={19} /></button></div><h1>收货地址</h1><div><button className="text-button address-page__add" type="button" onClick={() => navigate('/mine/addresses/new')}><Plus size={17} />新增地址</button></div></header>
    <section className="subpage-content address-page__content"><div className="discovery-input address-search"><Search size={20} /><input aria-label="搜索收货地址" placeholder="搜索地址、收件人" value={keyword} onChange={(event) => setKeyword(event.target.value)} />{keyword && <button className="icon-button" aria-label="清空搜索内容" type="button" onClick={() => setKeyword('')}><X size={18} /></button>}</div>
      {loading && <p className="empty-state">正在读取收货地址...</p>}
      {!loading && visibleAddresses.map((address) => <article className="address-card" key={address.id}><div className="address-card__main"><p className="address-card__region">{address.provinceName}{address.city}{address.district || ''}</p><h2>{address.detailAddress}</h2><p className="address-card__receiver">{address.receiverName}　{address.receiverPhone}{address.isDefault && <em>默认</em>}</p></div><button className="icon-button address-card__menu" type="button" aria-label={`操作${address.detailAddress}`} aria-expanded={actionAddress?.id === address.id} onClick={() => setActionAddress((current) => current?.id === address.id ? undefined : address)}><Ellipsis size={23} /></button>{actionAddress?.id === address.id && <div className="address-card__quick-menu" role="menu"><button type="button" role="menuitem" onClick={editAddress}><Pencil size={15} />编辑</button><button className="danger" type="button" role="menuitem" onClick={requestDeleteAddress}><Trash2 size={15} />删除</button></div>}</article>)}
      {!loading && !visibleAddresses.length && <div className="address-empty"><MapPin size={34} /><p>{keyword ? '未找到匹配的收货地址' : '暂无收货地址'}</p>{!keyword && <button className="primary-button" type="button" onClick={() => navigate('/mine/addresses/new')}>新增地址</button>}</div>}
    </section>{deleteAddress && <Dialog title="确认删除地址" onClose={() => !operatingId && setDeleteAddress(undefined)}><div className="address-delete-confirm"><p>删除后无法恢复，确认删除“{deleteAddress.detailAddress}”吗？</p><div><button className="secondary-button" disabled={Boolean(operatingId)} type="button" onClick={() => setDeleteAddress(undefined)}>取消</button><button className="primary-button" disabled={Boolean(operatingId)} type="button" onClick={() => void confirmDeleteAddress()}>{operatingId === `delete:${deleteAddress.id}` ? '删除中...' : '确认删除'}</button></div></div></Dialog>}{notice && <div className="toast" role="status" onClick={() => setNotice('')}>{notice}</div>}
  </main>;
}

/** 判断接口异常是否为需刷新服务端状态的冲突。 */
function isConflictError(error: unknown): boolean {
  return typeof error === 'object' && error !== null && 'status' in error && error.status === 409;
}
