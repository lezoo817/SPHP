import { describe, expect, it } from 'vitest';
import { clearDismissedCompletedConsultationTodos, dismissCompletedConsultationTodo, getDismissedCompletedConsultationTodoIds, isCompletedConsultationTodoDismissed, type CompletedConsultationTodoStorage } from './completed-consultation-health-todo';

/** 创建用于验证问诊待办已查看状态的最小内存存储。 */
function createStorage(): CompletedConsultationTodoStorage {
  const values = new Map<string, string>();
  return { getItem: (key) => values.get(key) || null, setItem: (key, value) => { values.set(key, value); }, removeItem: (key) => { values.delete(key); } };
}

describe('已完成问诊首页待办', () => {
  it('点击后记录已查看，并在当前会话中可恢复关闭状态', () => {
    const storage = createStorage();
    const todo = { id: 801, patientId: 2001 };
    const dismissedIds = dismissCompletedConsultationTodo(todo, storage);
    expect(isCompletedConsultationTodoDismissed(todo, dismissedIds)).toBe(true);
    expect(getDismissedCompletedConsultationTodoIds(storage)).toEqual(dismissedIds);
    clearDismissedCompletedConsultationTodos(storage);
    expect(getDismissedCompletedConsultationTodoIds(storage)).toEqual([]);
  });
});
