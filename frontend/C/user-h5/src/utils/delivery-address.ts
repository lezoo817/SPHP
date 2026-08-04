import type { DeliveryAddressPayload } from '../typings/api';
import { createIdempotencyKey } from './form';

/** 地区选择器中的省级地区与城市列表。 */
export interface DeliveryProvince { code: string; name: string; sortKey: string; cities: string[]; }

/** 后端当前允许写入和配送计算的省市编码。 */
const supportedProvinceCodes = new Set(['HENAN', 'SHANGHAI', 'BEIJING', 'JIANGSU', 'ZHEJIANG', 'GUANGDONG']);

/** 全国省级地区静态数据；每项城市数量限制为 15，直辖市只保留本市。 */
const provinces: DeliveryProvince[] = [
  { code: 'ANHUI', name: '安徽省', sortKey: 'anhui', cities: ['合肥市', '芜湖市', '蚌埠市', '淮南市', '马鞍山市', '淮北市', '铜陵市', '安庆市', '黄山市', '滁州市', '阜阳市', '宿州市', '六安市', '亳州市', '池州市'] },
  { code: 'MACAO', name: '澳门特别行政区', sortKey: 'aomen', cities: ['澳门特别行政区'] },
  { code: 'BEIJING', name: '北京市', sortKey: 'beijing', cities: ['北京市'] },
  { code: 'CHONGQING', name: '重庆市', sortKey: 'chongqing', cities: ['重庆市'] },
  { code: 'FUJIAN', name: '福建省', sortKey: 'fujian', cities: ['福州市', '厦门市', '莆田市', '三明市', '泉州市', '漳州市', '南平市', '龙岩市', '宁德市'] },
  { code: 'GANSU', name: '甘肃省', sortKey: 'gansu', cities: ['兰州市', '嘉峪关市', '金昌市', '白银市', '天水市', '武威市', '张掖市', '平凉市', '酒泉市', '庆阳市', '定西市', '陇南市'] },
  { code: 'GUANGDONG', name: '广东省', sortKey: 'guangdong', cities: ['广州市', '韶关市', '深圳市', '珠海市', '汕头市', '佛山市', '江门市', '湛江市', '茂名市', '肇庆市', '惠州市', '梅州市', '汕尾市', '河源市', '阳江市'] },
  { code: 'GUANGXI', name: '广西壮族自治区', sortKey: 'guangxi', cities: ['南宁市', '柳州市', '桂林市', '梧州市', '北海市', '防城港市', '钦州市', '贵港市', '玉林市', '百色市', '贺州市', '河池市', '来宾市', '崇左市'] },
  { code: 'GUIZHOU', name: '贵州省', sortKey: 'guizhou', cities: ['贵阳市', '六盘水市', '遵义市', '安顺市', '毕节市', '铜仁市', '兴义市', '凯里市', '都匀市'] },
  { code: 'HAINAN', name: '海南省', sortKey: 'hainan', cities: ['海口市', '三亚市', '三沙市', '儋州市'] },
  { code: 'HEBEI', name: '河北省', sortKey: 'hebei', cities: ['石家庄市', '唐山市', '秦皇岛市', '邯郸市', '邢台市', '保定市', '张家口市', '承德市', '沧州市', '廊坊市', '衡水市'] },
  { code: 'HEILONGJIANG', name: '黑龙江省', sortKey: 'heilongjiang', cities: ['哈尔滨市', '齐齐哈尔市', '鸡西市', '鹤岗市', '双鸭山市', '大庆市', '伊春市', '佳木斯市', '七台河市', '牡丹江市', '黑河市', '绥化市'] },
  { code: 'HENAN', name: '河南省', sortKey: 'henan', cities: ['郑州市', '开封市', '洛阳市', '平顶山市', '安阳市', '鹤壁市', '新乡市', '焦作市', '濮阳市', '许昌市', '漯河市', '三门峡市', '南阳市', '商丘市', '信阳市'] },
  { code: 'HUBEI', name: '湖北省', sortKey: 'hubei', cities: ['武汉市', '黄石市', '十堰市', '宜昌市', '襄阳市', '鄂州市', '荆门市', '孝感市', '荆州市', '黄冈市', '咸宁市', '随州市', '恩施市'] },
  { code: 'HUNAN', name: '湖南省', sortKey: 'hunan', cities: ['长沙市', '株洲市', '湘潭市', '衡阳市', '邵阳市', '岳阳市', '常德市', '张家界市', '益阳市', '郴州市', '永州市', '怀化市', '娄底市', '吉首市'] },
  { code: 'JILIN', name: '吉林省', sortKey: 'jilin', cities: ['长春市', '吉林市', '四平市', '辽源市', '通化市', '白山市', '松原市', '白城市', '延吉市'] },
  { code: 'JIANGSU', name: '江苏省', sortKey: 'jiangsu', cities: ['南京市', '无锡市', '徐州市', '常州市', '苏州市', '南通市', '连云港市', '淮安市', '盐城市', '扬州市', '镇江市', '泰州市', '宿迁市'] },
  { code: 'JIANGXI', name: '江西省', sortKey: 'jiangxi', cities: ['南昌市', '景德镇市', '萍乡市', '九江市', '新余市', '鹰潭市', '赣州市', '吉安市', '宜春市', '抚州市', '上饶市'] },
  { code: 'LIAONING', name: '辽宁省', sortKey: 'liaoning', cities: ['沈阳市', '大连市', '鞍山市', '抚顺市', '本溪市', '丹东市', '锦州市', '营口市', '阜新市', '辽阳市', '盘锦市', '铁岭市', '朝阳市', '葫芦岛市'] },
  { code: 'NEIMENGGU', name: '内蒙古自治区', sortKey: 'neimenggu', cities: ['呼和浩特市', '包头市', '乌海市', '赤峰市', '通辽市', '鄂尔多斯市', '呼伦贝尔市', '巴彦淖尔市', '乌兰察布市'] },
  { code: 'NINGXIA', name: '宁夏回族自治区', sortKey: 'ningxia', cities: ['银川市', '石嘴山市', '吴忠市', '固原市', '中卫市'] },
  { code: 'QINGHAI', name: '青海省', sortKey: 'qinghai', cities: ['西宁市', '海东市'] },
  { code: 'SHAANXI', name: '陕西省', sortKey: 'shaanxi', cities: ['西安市', '铜川市', '宝鸡市', '咸阳市', '渭南市', '延安市', '汉中市', '榆林市', '安康市', '商洛市'] },
  { code: 'SHANDONG', name: '山东省', sortKey: 'shandong', cities: ['济南市', '青岛市', '淄博市', '枣庄市', '东营市', '烟台市', '潍坊市', '济宁市', '泰安市', '威海市', '日照市', '临沂市', '德州市', '聊城市', '滨州市'] },
  { code: 'SHANGHAI', name: '上海市', sortKey: 'shanghai', cities: ['上海市'] },
  { code: 'SHANXI', name: '山西省', sortKey: 'shanxi', cities: ['太原市', '大同市', '阳泉市', '长治市', '晋城市', '朔州市', '晋中市', '运城市', '忻州市', '临汾市', '吕梁市'] },
  { code: 'SICHUAN', name: '四川省', sortKey: 'sichuan', cities: ['成都市', '自贡市', '攀枝花市', '泸州市', '德阳市', '绵阳市', '广元市', '遂宁市', '内江市', '乐山市', '南充市', '眉山市', '宜宾市', '广安市', '达州市'] },
  { code: 'TAIWAN', name: '台湾省', sortKey: 'taiwan', cities: ['台北市', '高雄市', '台中市', '台南市', '新北市'] },
  { code: 'TIANJIN', name: '天津市', sortKey: 'tianjin', cities: ['天津市'] },
  { code: 'XIANGGANG', name: '香港特别行政区', sortKey: 'xianggang', cities: ['香港特别行政区'] },
  { code: 'XINJIANG', name: '新疆维吾尔自治区', sortKey: 'xinjiang', cities: ['乌鲁木齐市', '克拉玛依市', '吐鲁番市', '哈密市', '阿克苏市', '喀什市', '和田市'] },
  { code: 'XIZANG', name: '西藏自治区', sortKey: 'xizang', cities: ['拉萨市', '日喀则市', '昌都市', '林芝市', '山南市', '那曲市'] },
  { code: 'YUNNAN', name: '云南省', sortKey: 'yunnan', cities: ['昆明市', '曲靖市', '玉溪市', '保山市', '昭通市', '丽江市', '普洱市', '临沧市', '楚雄市', '大理市'] },
  { code: 'ZHEJIANG', name: '浙江省', sortKey: 'zhejiang', cities: ['杭州市', '宁波市', '温州市', '嘉兴市', '湖州市', '绍兴市', '金华市', '衢州市', '舟山市', '台州市', '丽水市'] },
];

/** 获取按拼音首字母排序的全国省级地区副本。 */
export function getDeliveryProvinces(): DeliveryProvince[] {
  return [...provinces].sort((left, right) => left.sortKey.localeCompare(right.sortKey));
}

/** 获取某省级地区最多 15 个城市的展示列表。 */
export function getDeliveryCities(provinceCode: string): string[] {
  return provinces.find((item) => item.code === provinceCode)?.cities.slice(0, 15) || [];
}

/** 判断地区编码是否可被当前后端地址接口保存。 */
export function isSupportedDeliveryProvince(provinceCode: string): boolean {
  return supportedProvinceCodes.has(provinceCode);
}

/** 校验地址表单与当前后端配送范围。 */
export function validateDeliveryAddress(values: DeliveryAddressPayload): string | undefined {
  if (!values.receiverName.trim()) return '请填写收件人姓名';
  if (values.receiverName.trim().length > 64) return '收件人姓名不能超过 64 个字符';
  if (!/^1[3-9]\d{9}$/.test(values.receiverPhone)) return '收件人手机号格式不正确';
  if (!values.province || !values.city) return '请选择所在地区';
  if (!isSupportedDeliveryProvince(values.province)) return '当前地区暂不支持配送';
  if (!values.detailAddress.trim()) return '请填写详细地址';
  return values.detailAddress.trim().length > 200 ? '详细地址不能超过 200 个字符' : undefined;
}

/** 清理表单空白文本，并保留编辑地址已有的区县。 */
export function buildDeliveryAddressPayload(values: DeliveryAddressPayload): DeliveryAddressPayload {
  return { receiverName: values.receiverName.trim(), receiverPhone: values.receiverPhone.trim(), province: values.province, city: values.city.trim(), district: values.district?.trim() || undefined, detailAddress: values.detailAddress.trim() };
}

/** 为同一地址写操作生成或复用幂等键。 */
export function resolveDeliveryIdempotencyKey(existingKey?: string): string {
  return existingKey || createIdempotencyKey();
}
