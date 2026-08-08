/** 当前跨页面就诊人和医院选择。 */
export interface SelectionState { patientId?: number; hospitalId?: number; }
const KEY = 'sphp_c_selection';
/** 读取会话内已选就诊人和医院。 */
export function getSelection(): SelectionState { try { return JSON.parse(sessionStorage.getItem(KEY) || '{}') as SelectionState; } catch { return {}; } }
/** 更新当前就诊人或医院，供挂号与问诊页面统一使用。 */
export function saveSelection(next: SelectionState): void { sessionStorage.setItem(KEY, JSON.stringify({ ...getSelection(), ...next })); }

/**
 * 清除当前会话的跨页面就诊人和医院选择。
 * 登录账号切换或退出时调用，避免新账号继承旧账号的医疗上下文。
 */
export function clearSelection(): void {
  if (typeof window !== 'undefined') window.sessionStorage.removeItem(KEY);
}

/** 从本人和家属列表中优先解析当前登录用户本人 ID。 */
export function resolveSelfPatientId(members: { patientId: number; relation: string; isDefault?: boolean }[]): number | undefined {
  return members.find((member) => member.relation === 'SELF')?.patientId || members.find((member) => member.isDefault)?.patientId || members[0]?.patientId;
}

/**
 * 解析当前账号应使用的就诊人。
 * @param members 当前账号可访问的本人和家属列表
 * @param selectedPatientId 会话中已有的就诊人选择
 * @returns 仍属于当前账号的已选 ID，否则回退到本人
 */
export function resolveSelectedPatientId(
  members: { patientId: number; relation: string; isDefault?: boolean }[],
  selectedPatientId?: number,
): number | undefined {
  // 只有旧选择仍存在于当前账号成员列表时才保留，防止切换账号后显示未选择。
  if (selectedPatientId !== undefined && members.some((member) => member.patientId === selectedPatientId)) return selectedPatientId;
  return resolveSelfPatientId(members);
}
