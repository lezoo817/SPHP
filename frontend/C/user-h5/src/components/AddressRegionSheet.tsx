import { useState } from 'react';
import { ChevronLeft, ChevronRight, X } from 'lucide-react';
import { getDeliveryCities, getDeliveryProvinces, isSupportedDeliveryProvince, type DeliveryProvince } from '../utils/delivery-address';

/** 地区选择完成后返回省编码、展示名称与城市名称。 */
export interface DeliveryRegionValue { province: string; provinceName: string; city: string; }

/** 地区选择器的入参与关闭事件。 */
interface AddressRegionSheetProps { value?: DeliveryRegionValue; onClose: () => void; onSelect: (value: DeliveryRegionValue) => void; }

/** 从底部展示全国省级地区和城市的两级选择器，不提供区县步骤。 */
export function AddressRegionSheet({ value, onClose, onSelect }: AddressRegionSheetProps) {
  const [selectedProvince, setSelectedProvince] = useState<DeliveryProvince | undefined>(() => getDeliveryProvinces().find((item) => item.code === value?.province));
  const [selectingCity, setSelectingCity] = useState(Boolean(value?.province));
  const provinces = getDeliveryProvinces();

  /** 选择省级地区后进入其城市列表，保留不支持地区以便用户浏览。 */
  function chooseProvince(province: DeliveryProvince) {
    setSelectedProvince(province);
    setSelectingCity(true);
  }

  /** 选择城市并将两级结果回填到地址表单。 */
  function chooseCity(city: string) {
    if (!selectedProvince) return;
    onSelect({ province: selectedProvince.code, provinceName: selectedProvince.name, city });
  }

  return <div className="region-sheet-mask" role="presentation" onMouseDown={onClose}><section className="region-sheet" role="dialog" aria-modal="true" aria-label="选择所在地区" onMouseDown={(event) => event.stopPropagation()}>
    <header className="region-sheet__header"><div>{selectingCity && <button className="icon-button" type="button" aria-label="返回省份列表" onClick={() => setSelectingCity(false)}><ChevronLeft size={22} /></button>}<h2>请选择所在地区</h2></div><button className="icon-button" type="button" aria-label="关闭地区选择" onClick={onClose}><X size={23} /></button></header>
    <div className="region-sheet__selected"><button className={!selectingCity ? 'active' : ''} type="button" onClick={() => setSelectingCity(false)}>{selectedProvince?.name || '请选择省份'}</button><ChevronRight size={17} />{selectingCity && <b>{value?.province === selectedProvince?.code ? value?.city : '请选择城市'}</b>}</div>
    {!selectingCity && <div className="region-list">{provinces.map((province, index) => <div className="region-list__group" key={province.code}>{(index === 0 || provinces[index - 1].sortKey[0] !== province.sortKey[0]) && <span className="region-list__letter">{province.sortKey[0].toUpperCase()}</span>}<button className={province.code === selectedProvince?.code ? 'selected' : ''} type="button" onClick={() => chooseProvince(province)}><span>{province.name}</span>{!isSupportedDeliveryProvince(province.code) && <small>暂不支持配送</small>}<ChevronRight size={18} /></button></div>)}</div>}
    {selectingCity && selectedProvince && <div className="region-list region-list--cities"><p className="region-list__title">选择城市</p>{getDeliveryCities(selectedProvince.code).map((city) => <button className={value?.province === selectedProvince.code && value.city === city ? 'selected' : ''} key={city} type="button" onClick={() => chooseCity(city)}><span>{city}</span>{value?.province === selectedProvince.code && value.city === city && <b>已选择</b>}</button>)}</div>}
  </section></div>;
}
