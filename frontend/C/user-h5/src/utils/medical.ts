/** 将后端分单位金额转换为页面展示的元金额。 */
export function formatAmount(amountCent: number): string {
  return `${(amountCent / 100).toFixed(2)} 元`;
}

/** 按中文拼音顺序返回医院副本，避免修改接口原始数据。 */
export function sortHospitals<T extends { name: string }>(hospitals: T[]): T[] {
  return [...hospitals].sort((left, right) => left.name.localeCompare(right.name, 'zh-CN'));
}

/** 按不区分空白的关键词筛选医院名称。 */
export function filterHospitals<T extends { name: string }>(hospitals: T[], keyword: string): T[] {
  const normalizedKeyword = keyword.trim();
  return normalizedKeyword ? hospitals.filter((hospital) => hospital.name.includes(normalizedKeyword)) : hospitals;
}

/** 将 ISO-8601 时间转换为面向患者的简明时间。 */
export function formatMedicalTime(value?: string): string {
  if (!value) return '时间待确认';
  return new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit' }).format(new Date(value));
}

/** 计算支付到期时间剩余秒数，过期时返回零。 */
export function getRemainingSeconds(expireAt?: string): number {
  return expireAt ? Math.max(0, Math.floor((new Date(expireAt).getTime() - Date.now()) / 1000)) : 0;
}
