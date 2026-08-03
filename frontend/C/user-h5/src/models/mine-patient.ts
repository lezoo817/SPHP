/** “我的”页面独立保存的当前就诊人键名。 */
const MINE_PATIENT_KEY = 'sphp_c_mine_patient_id';

/** 就诊人选择回退所需的最小字段。 */
export interface MinePatientCandidate { patientId: number; relation: string; isDefault?: boolean; }

/**
 * 读取“我的”页面已选就诊人 ID。
 * @returns 会话内保存的就诊人 ID；无效或不存在时返回 undefined
 */
export function getMinePatientId(): number | undefined {
  if (typeof window === 'undefined') return undefined;
  const value = Number(window.sessionStorage.getItem(MINE_PATIENT_KEY));
  return Number.isInteger(value) && value > 0 ? value : undefined;
}

/**
 * 保存“我的”页面当前就诊人，不写入其他页面使用的全局选择。
 * @param patientId 当前选择的本人或家属 ID
 */
export function saveMinePatientId(patientId: number): void {
  if (typeof window !== 'undefined') window.sessionStorage.setItem(MINE_PATIENT_KEY, String(patientId));
}

/**
 * 从有效成员中解析“我的”页面当前就诊人。
 * @param members 当前接口返回的有效就诊人列表
 * @param selectedPatientId 会话中曾选择的就诊人 ID
 * @returns 仍有效的已选成员，否则优先本人、默认成员或首个成员
 */
export function resolveMinePatientId(members: MinePatientCandidate[], selectedPatientId = getMinePatientId()): number | undefined {
  if (selectedPatientId && members.some((member) => member.patientId === selectedPatientId)) return selectedPatientId;
  return members.find((member) => member.relation === 'SELF')?.patientId
    || members.find((member) => member.isDefault)?.patientId
    || members[0]?.patientId;
}
