/**
 * 构建在线问诊详情路径，并透传当前就诊人。
 * @param consultationId 问诊记录 ID
 * @param patientId 当前就诊人 ID
 * @returns 可恢复患者购药上下文的问诊详情路径
 */
export function buildConsultationDetailPath(consultationId: number, patientId?: number): string {
  const query = new URLSearchParams();
  if (Number.isInteger(patientId) && patientId! > 0) query.set('patientId', String(patientId));
  const queryText = query.toString();
  return `/assistant/consultation/${consultationId}${queryText ? `?${queryText}` : ''}`;
}

/**
 * 将问诊消息时间格式化为上海时区的 YYYY/MM/DD hh:mm。
 * @param value 后端返回的 ISO 时间
 * @returns 面向患者的分钟级时间；无效值返回时间待确认
 */
export function formatConsultationMessageTime(value?: string): string {
  const date = value ? new Date(value) : undefined;
  if (!date || Number.isNaN(date.getTime())) return '时间待确认';
  const parts = new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).formatToParts(date).reduce<Record<string, string>>((result, part) => {
    result[part.type] = part.value;
    return result;
  }, {});
  return `${parts.year}/${parts.month}/${parts.day} ${parts.hour}:${parts.minute}`;
}
