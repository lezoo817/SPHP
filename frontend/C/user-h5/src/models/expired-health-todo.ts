/** 已关闭过期健康待办在浏览器会话中的存储键。 */
const DISMISSED_EXPIRED_HEALTH_TODO_KEY = 'sphp_c_dismissed_expired_health_todos';

/** 过期健康待办生成关闭标识所需的最小字段。 */
export interface ExpiredHealthTodoIdentity {
  /** 待办类型。 */
  type: string;
  /** 待办业务 ID。 */
  id: number;
  /** 待办所属就诊人 ID。 */
  patientId: number;
}

/** 读取和写入关闭标识所需的最小会话存储接口。 */
export interface ExpiredHealthTodoStorage {
  /** 读取指定键。 */
  getItem(key: string): string | null;
  /** 写入指定键。 */
  setItem(key: string, value: string): void;
  /** 删除指定键。 */
  removeItem(key: string): void;
}

/**
 * 读取当前会话中已关闭的过期健康待办标识。
 * @param storage 可选会话存储实现，便于单元测试注入
 * @returns 有效的已关闭待办标识列表
 */
export function getDismissedExpiredHealthTodoIds(storage = getExpiredHealthTodoStorage()): string[] {
  if (!storage) return [];
  try {
    const parsed: unknown = JSON.parse(storage.getItem(DISMISSED_EXPIRED_HEALTH_TODO_KEY) || '[]');
    // 只保留字符串标识，避免异常会话数据影响首页待办加载。
    return Array.isArray(parsed) ? parsed.filter((item): item is string => typeof item === 'string') : [];
  } catch {
    storage.removeItem(DISMISSED_EXPIRED_HEALTH_TODO_KEY);
    return [];
  }
}

/**
 * 将单个过期健康待办标记为当前会话已关闭。
 * @param todo 被患者关闭的过期待办
 * @param storage 可选会话存储实现，便于单元测试注入
 * @returns 写入后的全部已关闭标识
 */
export function dismissExpiredHealthTodo(todo: ExpiredHealthTodoIdentity, storage = getExpiredHealthTodoStorage()): string[] {
  const current = getDismissedExpiredHealthTodoIds(storage);
  const todoId = createExpiredHealthTodoId(todo);
  const next = current.includes(todoId) ? current : [...current, todoId];
  // 关闭动作仅影响当前浏览器会话，不向后端写入订单或就诊状态。
  storage?.setItem(DISMISSED_EXPIRED_HEALTH_TODO_KEY, JSON.stringify(next));
  return next;
}

/**
 * 判断过期待办是否已被当前会话关闭。
 * @param todo 当前待办身份信息
 * @param dismissedIds 当前会话已关闭标识
 * @returns 标识存在时返回 true
 */
export function isExpiredHealthTodoDismissed(todo: ExpiredHealthTodoIdentity, dismissedIds: string[]): boolean {
  return dismissedIds.includes(createExpiredHealthTodoId(todo));
}

/**
 * 清除当前会话的过期健康待办关闭状态。
 * @param storage 可选会话存储实现，便于单元测试注入
 */
export function clearDismissedExpiredHealthTodos(storage = getExpiredHealthTodoStorage()): void {
  storage?.removeItem(DISMISSED_EXPIRED_HEALTH_TODO_KEY);
}

/** 根据待办类型、就诊人和业务 ID 生成稳定的关闭标识。 */
function createExpiredHealthTodoId({ type, patientId, id }: ExpiredHealthTodoIdentity): string {
  return `${type}:${patientId}:${id}`;
}

/** 获取浏览器会话存储；服务端渲染或隐私限制下返回 undefined。 */
function getExpiredHealthTodoStorage(): ExpiredHealthTodoStorage | undefined {
  try {
    return typeof window === 'undefined' ? undefined : window.sessionStorage;
  } catch {
    return undefined;
  }
}
