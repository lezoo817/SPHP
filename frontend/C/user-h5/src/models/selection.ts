/** 当前跨页面就诊人和医院选择。 */
export interface SelectionState {
  /** 全局当前就诊人 ID。 */
  patientId?: number;
  /** 全局当前医院 ID。 */
  hospitalId?: number;
}

/** 当前账号会话内全局就诊人与医院选择的存储键。 */
const SELECTION_STORAGE_KEY = 'sphp_c_selection';

/** 全局选择读写所需的最小会话存储接口。 */
interface SelectionStorage {
  /** 读取指定键的值。 */
  getItem(key: string): string | null;
  /** 写入指定键的值。 */
  setItem(key: string, value: string): void;
  /** 删除指定键的值。 */
  removeItem(key: string): void;
}

/**
 * 读取会话内已选就诊人和医院。
 * @returns 已保存的全局选择；浏览器存储不可用或数据异常时返回空对象
 */
export function getSelection(): SelectionState {
  const storage = getSelectionStorage();
  if (!storage) return {};
  try {
    const parsed: unknown = JSON.parse(storage.getItem(SELECTION_STORAGE_KEY) || '{}');
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed as SelectionState : {};
  } catch {
    // 异常缓存不应阻断页面初始化，直接清除并让成员列表回退默认本人。
    storage.removeItem(SELECTION_STORAGE_KEY);
    return {};
  }
}

/**
 * 更新当前就诊人或医院，供首页、就诊助手、购药和“我的”统一使用。
 * @param next 需要覆盖的全局选择字段
 * @returns 无返回值
 */
export function saveSelection(next: SelectionState): void {
  const storage = getSelectionStorage();
  if (!storage) return;
  storage.setItem(SELECTION_STORAGE_KEY, JSON.stringify({ ...getSelection(), ...next }));
}

/**
 * 清除当前会话的跨页面就诊人和医院选择。
 * 登录账号切换或退出时调用，避免新账号继承旧账号的医疗上下文。
 */
export function clearSelection(): void {
  getSelectionStorage()?.removeItem(SELECTION_STORAGE_KEY);
}

/**
 * 从本人和家属列表中优先解析当前登录用户本人 ID。
 * @param members 当前账号可访问的就诊人列表
 * @returns 本人 ID；本人缺失时回退默认成员或首个成员
 */
export function resolveSelfPatientId(members: { patientId: number; relation: string; isDefault?: boolean }[]): number | undefined {
  return members.find((member) => member.relation === 'SELF')?.patientId
    || members.find((member) => member.isDefault)?.patientId
    || members[0]?.patientId;
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

/** 获取浏览器会话存储；服务端渲染或隐私限制下返回 undefined。 */
function getSelectionStorage(): SelectionStorage | undefined {
  try {
    return typeof window === 'undefined' ? undefined : window.sessionStorage;
  } catch {
    return undefined;
  }
}
