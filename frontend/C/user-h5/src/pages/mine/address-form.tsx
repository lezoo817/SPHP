import { FormEvent, useEffect, useRef, useState } from 'react';
import { ChevronRight } from 'lucide-react';
import { useNavigate, useParams } from 'umi';
import { AddressRegionSheet, type DeliveryRegionValue } from '../../components/AddressRegionSheet';
import { PageHeader } from '../../components/PageHeader';
import { createDeliveryAddress, getDeliveryAddresses, setDefaultDeliveryAddress, updateDeliveryAddress } from '../../services/delivery-address';
import type { DeliveryAddress, DeliveryAddressPayload } from '../../typings/api';
import { buildDeliveryAddressPayload, getDeliveryAddressInvalidFields, isSupportedDeliveryProvince, resolveDeliveryIdempotencyKey, validateDeliveryAddress } from '../../utils/delivery-address';
import { getApiErrorMessage } from '../../utils/form';

const emptyAddress: DeliveryAddressPayload = { receiverName: '', receiverPhone: '', province: '', city: '', detailAddress: '' };

/** 提供新增和编辑收货地址表单，以及两级地区选择。 */
export default function AddressFormPage() {
  const navigate = useNavigate();
  const params = useParams();
  const addressId = Number(params.addressId);
  const isEditing = Number.isInteger(addressId) && addressId > 0;
  const [form, setForm] = useState<DeliveryAddressPayload>(emptyAddress);
  const [provinceName, setProvinceName] = useState('');
  const [address, setAddress] = useState<DeliveryAddress>();
  const [makeDefault, setMakeDefault] = useState(false);
  const [regionOpen, setRegionOpen] = useState(false);
  const [loading, setLoading] = useState(isEditing);
  const [submitting, setSubmitting] = useState(false);
  const [hasSubmitAttempted, setHasSubmitAttempted] = useState(false);
  const [notice, setNotice] = useState('');
  const saveKey = useRef<string>();
  const defaultKey = useRef<string>();

  /** 编辑模式从当前账号地址列表中读取目标地址，避免使用未提供的详情接口。 */
  async function loadAddress() {
    if (!isEditing) return;
    setLoading(true);
    try {
      const target = (await getDeliveryAddresses()).find((item) => item.id === addressId);
      if (!target) {
        setNotice('收货地址不存在或已删除');
        return;
      }
      setAddress(target);
      setProvinceName(target.provinceName);
      // 不展示区县选择，但更新时必须保留服务端已有区县数据。
      setForm({ receiverName: target.receiverName, receiverPhone: target.receiverPhone, province: target.province, city: target.city, district: target.district, detailAddress: target.detailAddress });
    } catch (requestError) {
      setNotice(getApiErrorMessage(requestError));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void loadAddress(); }, [addressId]);

  /** 将地区选择结果写入表单，后端不支持地区会在保存时明确拦截。 */
  function selectRegion(value: DeliveryRegionValue) {
    setForm((current) => ({ ...current, province: value.province, city: value.city }));
    setProvinceName(value.provinceName);
    setRegionOpen(false);
  }

  /** 提交新增或编辑地址，并在用户勾选时追加默认地址设置请求。 */
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    // 首次保存尝试后同时标红全部不合规的必填项，便于一次完成修正。
    setHasSubmitAttempted(true);
    const message = validateDeliveryAddress(form);
    if (message) return setNotice(message);
    setSubmitting(true);
    const payload = buildDeliveryAddressPayload(form);
    saveKey.current = resolveDeliveryIdempotencyKey(saveKey.current);
    try {
      const saved = isEditing ? await updateDeliveryAddress(addressId, payload, saveKey.current) : await createDeliveryAddress(payload, saveKey.current);
      // 创建和设默认属于不同接口，必须使用不同幂等键防止跨路径误重放。
      if (makeDefault && !saved.isDefault) {
        defaultKey.current = resolveDeliveryIdempotencyKey(defaultKey.current);
        await setDefaultDeliveryAddress(saved.id, defaultKey.current);
      }
      saveKey.current = undefined;
      defaultKey.current = undefined;
      navigate('/mine/addresses');
    } catch (requestError) {
      setNotice(getApiErrorMessage(requestError));
    } finally {
      setSubmitting(false);
    }
  }

  const regionValue = form.province && form.city ? { province: form.province, provinceName, city: form.city } : undefined;
  const canChangeDefault = !address?.isDefault;
  const invalidFields = hasSubmitAttempted ? getDeliveryAddressInvalidFields(form) : [];
  return <main className="subpage address-form-page"><PageHeader title={isEditing ? '编辑收货地址' : '新增收货地址'} backPath="/mine/addresses" /><form className="subpage-content address-form" onSubmit={submit}>
    {loading ? <p className="empty-state">正在读取收货地址...</p> : <><button className={invalidFields.includes('region') ? 'address-region-field is-invalid' : 'address-region-field'} aria-invalid={invalidFields.includes('region')} type="button" onClick={() => setRegionOpen(true)}><span><b><i aria-hidden="true">*</i>所在地区</b><small>请选择省份和城市</small></span><strong>{provinceName && form.city ? `${provinceName} ${form.city}` : '请选择所在地区'}</strong><ChevronRight size={19} /></button>{form.province && !isSupportedDeliveryProvince(form.province) && <p className="form-error">当前地区暂不支持配送，请重新选择地区</p>}
      <label className={invalidFields.includes('detailAddress') ? 'is-invalid' : ''}><b><i aria-hidden="true">*</i>详细地址</b><input aria-invalid={invalidFields.includes('detailAddress')} value={form.detailAddress} maxLength={200} placeholder="街道、门牌号、楼栋等" onChange={(event) => setForm({ ...form, detailAddress: event.target.value })} /></label>
      <label className={invalidFields.includes('receiverName') ? 'is-invalid' : ''}><b><i aria-hidden="true">*</i>收件人姓名</b><input aria-invalid={invalidFields.includes('receiverName')} value={form.receiverName} maxLength={64} placeholder="请填写收件人姓名" onChange={(event) => setForm({ ...form, receiverName: event.target.value })} /></label>
      <label className={invalidFields.includes('receiverPhone') ? 'is-invalid' : ''}><b><i aria-hidden="true">*</i>手机号</b><input aria-invalid={invalidFields.includes('receiverPhone')} type="tel" inputMode="numeric" value={form.receiverPhone} maxLength={11} placeholder="请填写手机号" onChange={(event) => setForm({ ...form, receiverPhone: event.target.value.replace(/\D/g, '') })} /></label>
      <label className="address-default-toggle"><span><b>设为默认地址</b><small>{address?.isDefault ? '当前地址已是默认地址' : '下单时优先使用此地址'}</small></span><input aria-label="设为默认地址" checked={address?.isDefault || makeDefault} disabled={!canChangeDefault} type="checkbox" onChange={(event) => setMakeDefault(event.target.checked)} /></label>
      <button className="primary-button address-form__submit" disabled={submitting} type="submit">{submitting ? '保存中...' : '保存地址'}</button></>}
  </form>{regionOpen && <AddressRegionSheet value={regionValue} onClose={() => setRegionOpen(false)} onSelect={selectRegion} />}{notice && <div className="toast" role="status" onClick={() => setNotice('')}>{notice}</div>}</main>;
}
