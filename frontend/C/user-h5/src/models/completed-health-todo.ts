/** 首页一键完成待办在当前登录会话中的存储键。 */
const COMPLETED_HEALTH_TODO_KEY = 'sphp_c_completed_health_todos';

/** 生成首页待办完成标识所需的最小字段。 */
export interface CompletedHealthTodoIdentity {
  /** 待办业务类别。 */
  type: string;
  /** 待办业务 ID。 */
  id: number;
  /** 待办所属就诊人 ID。 */
  patientId: number;
  /** 用药提醒等按时段生成的待办时间。 */
  occurredAt?: string;
}

/** 读取和写入一键完成状态所需的最小会话存储接口。 */
export interface CompletedHealthTodoStorage {
  /** 读取指定键。 */
  getItem(key: string): string | null;
  /** 写入指定键。 */
  setItem(key: string, value: string): void;
  /** 删除指定键。 */
  removeItem(key: string): void;
}

/**
 * 读取当前会话已一键完成的首页待办标识。
 * @param storage 可选会话存储实现，便于单元测试注入
 * @returns 有效的待办标识数组
 */
export function getCompletedHealthTodoIds(storage = getCompletedHealthTodoStorage()): string[] {
  if (!storage) return [];
  try {
    const parsed: unknown = JSON.parse(storage.getItem(COMPLETED_HEALTH_TODO_KEY) || '[]');
    return Array.isArray(parsed) ? parsed.filter((item): item is string => typeof item === 'string') : [];
  } catch {
    storage.removeItem(COMPLETED_HEALTH_TODO_KEY);
    return [];
  }
}

/**
 * 将当前首页全部待办标记为已完成。
 * @param todos 当前首页展示的待办集合
 * @param storage 可选会话存储实现，便于单元测试注入
 * @returns 合并后的已完成待办标识数组
 */
export function completeAllHealthTodos(todos: CompletedHealthTodoIdentity[], storage = getCompletedHealthTodoStorage()): string[] {
  const current = getCompletedHealthTodoIds(storage);
  const next = Array.from(new Set([...current, ...todos.map(createCompletedHealthTodoId)]));
  // 一键完成仅关闭首页提醒，不更改挂号、问诊、用药或随访的服务端医疗状态。
  storage?.setItem(COMPLETED_HEALTH_TODO_KEY, JSON.stringify(next));
  return next;
}

/**
 * 判断待办是否已由首页一键完成。
 * @param todo 当前待办身份
 * @param completedIds 当前会话的已完成标识
 * @returns 已一键完成时返回 true
 */
export function isHealthTodoCompleted(todo: CompletedHealthTodoIdentity, completedIds: string[]): boolean {
  return completedIds.includes(createCompletedHealthTodoId(todo));
}

/**
 * 清除当前会话一键完成的首页待办状态。
 * @param storage 可选会话存储实现，便于单元测试注入
 */
export function clearCompletedHealthTodos(storage = getCompletedHealthTodoStorage()): void {
  storage?.removeItem(COMPLETED_HEALTH_TODO_KEY);
}

/** 根据类型、就诊人、业务 ID 和提醒时段生成稳定标识。 */
function createCompletedHealthTodoId({ type, patientId, id, occurredAt }: CompletedHealthTodoIdentity): string {
  return `${type}:${patientId}:${id}:${occurredAt || 'unscheduled'}`;
}

/** 获取浏览器会话存储；服务端渲染或隐私限制下返回 undefined。 */
function getCompletedHealthTodoStorage(): CompletedHealthTodoStorage | undefined {
  try {
    return typeof window === 'undefined' ? undefined : window.sessionStorage;
  } catch {
    return undefined;
  }
}
