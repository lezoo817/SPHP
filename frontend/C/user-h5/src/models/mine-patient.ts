import { getSelection, resolveSelectedPatientId, saveSelection } from './selection';

/** 就诊人选择回退所需的最小字段。 */
export interface MinePatientCandidate { patientId: number; relation: string; isDefault?: boolean; }

/**
 * 读取项目全局已选就诊人 ID。
 * @returns 会话内保存的全局就诊人 ID；无效或不存在时返回 undefined
 */
export function getMinePatientId(): number | undefined {
  return getSelection().patientId;
}

/**
 * 保存项目全局当前就诊人。
 * @param patientId 当前选择的本人或家属 ID
 */
export function saveMinePatientId(patientId: number): void {
  // 保留旧函数名以兼容“我的”子页面，但底层统一写入首页、助手和购药共用的选择。
  saveSelection({ patientId });
}

/**
 * 从有效成员中解析项目全局当前就诊人。
 * @param members 当前接口返回的有效就诊人列表
 * @param selectedPatientId 会话中曾选择的就诊人 ID
 * @returns 仍有效的已选成员，否则优先本人、默认成员或首个成员
 */
export function resolveMinePatientId(members: MinePatientCandidate[], selectedPatientId = getMinePatientId()): number | undefined {
  return resolveSelectedPatientId(members, selectedPatientId);
}
