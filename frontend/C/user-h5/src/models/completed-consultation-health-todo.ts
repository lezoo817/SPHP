/** 已查看的已完成问诊首页待办存储键。 */
const DISMISSED_COMPLETED_CONSULTATION_TODO_KEY = 'sphp_c_dismissed_completed_consultation_todos';

/** 已完成问诊待办归属所需的最小字段。 */
export interface CompletedConsultationTodoIdentity { id: number; patientId: number; }

/** 可注入的会话存储接口，便于单元测试。 */
export interface CompletedConsultationTodoStorage { getItem(key: string): string | null; setItem(key: string, value: string): void; removeItem(key: string): void; }

/**
 * 读取当前会话已查看的已完成问诊待办。
 * @param storage 可选会话存储
 * @returns 稳定的待办标识数组
 */
export function getDismissedCompletedConsultationTodoIds(storage = getCompletedConsultationTodoStorage()): string[] {
  if (!storage) return [];
  try {
    const parsed: unknown = JSON.parse(storage.getItem(DISMISSED_COMPLETED_CONSULTATION_TODO_KEY) || '[]');
    return Array.isArray(parsed) ? parsed.filter((item): item is string => typeof item === 'string') : [];
  } catch {
    storage.removeItem(DISMISSED_COMPLETED_CONSULTATION_TODO_KEY);
    return [];
  }
}

/**
 * 将已完成问诊待办标记为已查看。
 * @param todo 当前问诊待办
 * @param storage 可选会话存储
 * @returns 更新后的待办标识数组
 */
export function dismissCompletedConsultationTodo(todo: CompletedConsultationTodoIdentity, storage = getCompletedConsultationTodoStorage()): string[] {
  const current = getDismissedCompletedConsultationTodoIds(storage);
  const id = createCompletedConsultationTodoId(todo);
  const next = current.includes(id) ? current : [...current, id];
  // 仅记录首页提醒已查看，不修改问诊完成状态或医疗记录。
  storage?.setItem(DISMISSED_COMPLETED_CONSULTATION_TODO_KEY, JSON.stringify(next));
  return next;
}

/** 判断问诊待办是否已查看。 */
export function isCompletedConsultationTodoDismissed(todo: CompletedConsultationTodoIdentity, dismissedIds: string[]): boolean {
  return dismissedIds.includes(createCompletedConsultationTodoId(todo));
}

/** 清除当前登录会话已查看的问诊待办。 */
export function clearDismissedCompletedConsultationTodos(storage = getCompletedConsultationTodoStorage()): void {
  storage?.removeItem(DISMISSED_COMPLETED_CONSULTATION_TODO_KEY);
}

/** 生成按就诊人和问诊 ID 隔离的待办标识。 */
function createCompletedConsultationTodoId({ patientId, id }: CompletedConsultationTodoIdentity): string { return `${patientId}:${id}`; }

/** 获取浏览器会话存储。 */
function getCompletedConsultationTodoStorage(): CompletedConsultationTodoStorage | undefined {
  try { return typeof window === 'undefined' ? undefined : window.sessionStorage; } catch { return undefined; }
}
