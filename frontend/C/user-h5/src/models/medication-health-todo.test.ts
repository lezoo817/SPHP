import { describe, expect, it } from 'vitest';
import { dismissMedicationHealthTodo, getMedicationHealthTodoState, getMedicationHealthTodoStates, isMedicationHealthTodoDismissed, markMedicationHealthTodoTaken, type MedicationHealthTodoStorage } from './medication-health-todo';

/** 提供用于验证会话状态读写的最小内存存储实现。 */
function createStorage(): MedicationHealthTodoStorage {
  const values = new Map<string, string>();
  return {
    getItem: (key) => values.get(key) || null,
    setItem: (key, value) => { values.set(key, value); },
    removeItem: (key) => { values.delete(key); },
  };
}

describe('首页用药待办服药确认', () => {
  it('首次点击标记已服用，第二次点击关闭当前提醒时段', () => {
    const storage = createStorage();
    const todo = { id: 101, patientId: 2001, occurredAt: '2026-08-10T08:00:00+08:00' };
    const takenStates = markMedicationHealthTodoTaken(todo, storage);
    expect(getMedicationHealthTodoState(todo, takenStates)).toBe('TAKEN');

    const dismissedStates = dismissMedicationHealthTodo(todo, storage);
    expect(isMedicationHealthTodoDismissed(todo, dismissedStates)).toBe(true);
  });

  it('同一计划的下一次提醒使用独立状态，不会被已服用记录关闭', () => {
    const storage = createStorage();
    const morningTodo = { id: 101, patientId: 2001, occurredAt: '2026-08-10T08:00:00+08:00' };
    const eveningTodo = { id: 101, patientId: 2001, occurredAt: '2026-08-10T20:00:00+08:00' };
    markMedicationHealthTodoTaken(morningTodo, storage);
    const states = getMedicationHealthTodoStates(storage);

    expect(getMedicationHealthTodoState(morningTodo, states)).toBe('TAKEN');
    expect(getMedicationHealthTodoState(eveningTodo, states)).toBeUndefined();
  });
});
