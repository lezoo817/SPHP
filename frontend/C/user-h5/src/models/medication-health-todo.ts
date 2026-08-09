/** 已服用或已关闭的首页用药待办在浏览器会话中的存储键。 */
const MEDICATION_HEALTH_TODO_STATE_KEY = 'sphp_c_medication_health_todo_states';

/** 首页用药待办的本地确认状态。 */
export type MedicationHealthTodoState = 'TAKEN' | 'DISMISSED';

/** 生成用药待办会话标识所需的最小字段。 */
export interface MedicationHealthTodoIdentity {
  /** 用药计划 ID。 */
  id: number;
  /** 用药计划所属就诊人 ID。 */
  patientId: number;
  /** 当前提醒时刻；下一次提醒变化后应视为新的待办。 */
  occurredAt?: string;
}

/** 读取和写入用药待办状态所需的最小会话存储接口。 */
export interface MedicationHealthTodoStorage {
  /** 读取指定键。 */
  getItem(key: string): string | null;
  /** 写入指定键。 */
  setItem(key: string, value: string): void;
  /** 删除指定键。 */
  removeItem(key: string): void;
}

/**
 * 读取当前会话中已确认的首页用药待办状态。
 * @param storage 可选会话存储实现，便于单元测试注入
 * @returns 以待办标识为键的有效状态集合
 */
export function getMedicationHealthTodoStates(storage = getMedicationHealthTodoStorage()): Record<string, MedicationHealthTodoState> {
  if (!storage) return {};
  try {
    const parsed: unknown = JSON.parse(storage.getItem(MEDICATION_HEALTH_TODO_STATE_KEY) || '{}');
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return {};
    // 仅接受两种明确状态，异常会话数据不能影响健康待办展示。
    return Object.fromEntries(Object.entries(parsed).filter((entry): entry is [string, MedicationHealthTodoState] => entry[1] === 'TAKEN' || entry[1] === 'DISMISSED'));
  } catch {
    storage.removeItem(MEDICATION_HEALTH_TODO_STATE_KEY);
    return {};
  }
}

/**
 * 获取单条首页用药待办的本地确认状态。
 * @param todo 当前用药待办身份
 * @param states 当前会话中已读取的状态集合
 * @returns 已服用、已关闭或 undefined
 */
export function getMedicationHealthTodoState(todo: MedicationHealthTodoIdentity, states: Record<string, MedicationHealthTodoState>): MedicationHealthTodoState | undefined {
  return states[createMedicationHealthTodoId(todo)];
}

/**
 * 将首页用药待办标记为已服用。
 * @param todo 当前用药待办身份
 * @param storage 可选会话存储实现，便于单元测试注入
 * @returns 写入后的完整状态集合
 */
export function markMedicationHealthTodoTaken(todo: MedicationHealthTodoIdentity, storage = getMedicationHealthTodoStorage()): Record<string, MedicationHealthTodoState> {
  return updateMedicationHealthTodoState(todo, 'TAKEN', storage);
}

/**
 * 关闭已服用的首页用药待办。
 * @param todo 当前用药待办身份
 * @param storage 可选会话存储实现，便于单元测试注入
 * @returns 写入后的完整状态集合
 */
export function dismissMedicationHealthTodo(todo: MedicationHealthTodoIdentity, storage = getMedicationHealthTodoStorage()): Record<string, MedicationHealthTodoState> {
  return updateMedicationHealthTodoState(todo, 'DISMISSED', storage);
}

/**
 * 判断用药待办是否已被当前会话关闭。
 * @param todo 当前用药待办身份
 * @param states 当前会话中已读取的状态集合
 * @returns 已关闭时返回 true
 */
export function isMedicationHealthTodoDismissed(todo: MedicationHealthTodoIdentity, states: Record<string, MedicationHealthTodoState>): boolean {
  return getMedicationHealthTodoState(todo, states) === 'DISMISSED';
}

/**
 * 清除当前会话的首页用药待办确认状态。
 * @param storage 可选会话存储实现，便于单元测试注入
 */
export function clearMedicationHealthTodoStates(storage = getMedicationHealthTodoStorage()): void {
  storage?.removeItem(MEDICATION_HEALTH_TODO_STATE_KEY);
}

/** 更新单条用药待办状态，并保留其他提醒时段的状态。 */
function updateMedicationHealthTodoState(todo: MedicationHealthTodoIdentity, state: MedicationHealthTodoState, storage: MedicationHealthTodoStorage | undefined): Record<string, MedicationHealthTodoState> {
  const next = { ...getMedicationHealthTodoStates(storage), [createMedicationHealthTodoId(todo)]: state };
  // 此操作仅记录本地服药确认，不改变后端用药计划或提醒开关状态。
  storage?.setItem(MEDICATION_HEALTH_TODO_STATE_KEY, JSON.stringify(next));
  return next;
}

/** 按就诊人、计划和提醒时刻生成稳定的会话标识。 */
function createMedicationHealthTodoId({ patientId, id, occurredAt }: MedicationHealthTodoIdentity): string {
  return `${patientId}:${id}:${occurredAt || 'unscheduled'}`;
}

/** 获取浏览器会话存储；服务端渲染或隐私限制下返回 undefined。 */
function getMedicationHealthTodoStorage(): MedicationHealthTodoStorage | undefined {
  try {
    return typeof window === 'undefined' ? undefined : window.sessionStorage;
  } catch {
    return undefined;
  }
}
