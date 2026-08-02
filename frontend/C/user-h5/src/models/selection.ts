/** 当前跨页面就诊人和医院选择。 */
export interface SelectionState { patientId?: number; hospitalId?: number; }
const KEY = 'sphp_c_selection';
/** 读取会话内已选就诊人和医院。 */
export function getSelection(): SelectionState { try { return JSON.parse(sessionStorage.getItem(KEY) || '{}') as SelectionState; } catch { return {}; } }
/** 更新当前就诊人或医院，供挂号与问诊页面统一使用。 */
export function saveSelection(next: SelectionState): void { sessionStorage.setItem(KEY, JSON.stringify({ ...getSelection(), ...next })); }
