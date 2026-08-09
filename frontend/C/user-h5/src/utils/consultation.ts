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
