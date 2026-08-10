import { describe, expect, it } from 'vitest';
import { clearCompletedHealthTodos, completeAllHealthTodos, getCompletedHealthTodoIds, isHealthTodoCompleted, type CompletedHealthTodoStorage } from './completed-health-todo';

/** 创建用于验证一键完成会话状态的最小内存存储。 */
function createStorage(): CompletedHealthTodoStorage {
  const values = new Map<string, string>();
  return {
    getItem: (key) => values.get(key) || null,
    setItem: (key, value) => { values.set(key, value); },
    removeItem: (key) => { values.delete(key); },
  };
}

describe('首页一键完成健康待办', () => {
  it('关闭当前全部待办，刷新后仍可按会话状态过滤', () => {
    const storage = createStorage();
    const todos = [
      { type: 'APPOINTMENT', id: 101, patientId: 2001, occurredAt: '2026-08-10T09:00:00+08:00' },
      { type: 'CONSULTATION', id: 102, patientId: 2001, occurredAt: '2026-08-10T10:00:00+08:00' },
      { type: 'MEDICATION', id: 103, patientId: 2002, occurredAt: '2026-08-10T08:00:00+08:00' },
    ];

    const completedIds = completeAllHealthTodos(todos, storage);

    expect(completedIds).toHaveLength(3);
    expect(todos.every((todo) => isHealthTodoCompleted(todo, completedIds))).toBe(true);
    expect(getCompletedHealthTodoIds(storage)).toEqual(completedIds);
  });

  it('同一计划不同时段独立记录，重复一键完成不重复写入', () => {
    const storage = createStorage();
    const morningTodo = { type: 'MEDICATION', id: 103, patientId: 2001, occurredAt: '2026-08-10T08:00:00+08:00' };
    const eveningTodo = { type: 'MEDICATION', id: 103, patientId: 2001, occurredAt: '2026-08-10T20:00:00+08:00' };

    const first = completeAllHealthTodos([morningTodo], storage);
    const second = completeAllHealthTodos([morningTodo], storage);

    expect(second).toEqual(first);
    expect(isHealthTodoCompleted(eveningTodo, second)).toBe(false);
    const next = completeAllHealthTodos([eveningTodo], storage);
    expect(next).toHaveLength(2);

    clearCompletedHealthTodos(storage);
    expect(getCompletedHealthTodoIds(storage)).toEqual([]);
  });
});
