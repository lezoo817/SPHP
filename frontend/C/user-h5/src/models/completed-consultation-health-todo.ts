/** 已查看的在线问诊首页待办存储键。 */
const DISMISSED_CONSULTATION_TODO_KEY = 'sphp_c_dismissed_consultation_todos';

/** 在线问诊待办归属所需的最小字段。 */
export interface ConsultationTodoIdentity { id: number; patientId: number; }

/** 可注入的会话存储接口，便于单元测试。 */
export interface ConsultationTodoStorage { getItem(key: string): string | null; setItem(key: string, value: string): void; removeItem(key: string): void; }

/**
 * 读取当前会话已查看的在线问诊待办。
 * @param storage 可选会话存储
 * @returns 稳定的待办标识数组
 */
export function getDismissedConsultationTodoIds(storage = getConsultationTodoStorage()): string[] {
  if (!storage) return [];
  try {
    const parsed: unknown = JSON.parse(storage.getItem(DISMISSED_CONSULTATION_TODO_KEY) || '[]');
    return Array.isArray(parsed) ? parsed.filter((item): item is string => typeof item === 'string') : [];
  } catch {
    storage.removeItem(DISMISSED_CONSULTATION_TODO_KEY);
    return [];
  }
}

/**
 * 将在线问诊待办标记为已查看。
 * @param todo 当前问诊待办
 * @param storage 可选会话存储
 * @returns 更新后的待办标识数组
 */
export function dismissConsultationTodo(todo: ConsultationTodoIdentity, storage = getConsultationTodoStorage()): string[] {
  const current = getDismissedConsultationTodoIds(storage);
  const id = createConsultationTodoId(todo);
  const next = current.includes(id) ? current : [...current, id];
  // 仅记录首页提醒已查看，不修改问诊状态或医疗记录。
  storage?.setItem(DISMISSED_CONSULTATION_TODO_KEY, JSON.stringify(next));
  return next;
}

/** 判断在线问诊待办是否已查看。 */
export function isConsultationTodoDismissed(todo: ConsultationTodoIdentity, dismissedIds: string[]): boolean {
  return dismissedIds.includes(createConsultationTodoId(todo));
}

/** 清除当前登录会话已查看的问诊待办。 */
export function clearDismissedConsultationTodos(storage = getConsultationTodoStorage()): void {
  storage?.removeItem(DISMISSED_CONSULTATION_TODO_KEY);
}

/** 生成按就诊人和问诊 ID 隔离的待办标识。 */
function createConsultationTodoId({ patientId, id }: ConsultationTodoIdentity): string { return `${patientId}:${id}`; }

/** 获取浏览器会话存储。 */
function getConsultationTodoStorage(): ConsultationTodoStorage | undefined {
  try { return typeof window === 'undefined' ? undefined : window.sessionStorage; } catch { return undefined; }
}
