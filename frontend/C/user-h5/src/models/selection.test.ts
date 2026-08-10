import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { getMinePatientId, resolveMinePatientId, saveMinePatientId } from './mine-patient';
import { clearSelection, getSelection, saveSelection } from './selection';

/** 创建用于验证全局就诊人会话状态的最小内存存储。 */
function createStorage(): Storage {
  const values = new Map<string, string>();
  return {
    get length() { return values.size; },
    clear: () => { values.clear(); },
    getItem: (key) => values.get(key) || null,
    key: (index) => Array.from(values.keys())[index] || null,
    removeItem: (key) => { values.delete(key); },
    setItem: (key, value) => { values.set(key, value); },
  };
}

describe('全局当前就诊人选择', () => {
  let originalStorage: Storage | undefined;
  let originalWindow: unknown;

  beforeEach(() => {
    originalStorage = globalThis.sessionStorage;
    originalWindow = globalThis.window;
    Object.defineProperty(globalThis, 'sessionStorage', { configurable: true, value: createStorage() });
    // 会话清理逻辑仅在浏览器环境执行，测试中补齐最小 window 以覆盖退出登录分支。
    Object.defineProperty(globalThis, 'window', { configurable: true, value: globalThis });
  });

  afterEach(() => {
    Object.defineProperty(globalThis, 'sessionStorage', { configurable: true, value: originalStorage });
    Object.defineProperty(globalThis, 'window', { configurable: true, value: originalWindow });
  });

  it('“我的”兼容选择与首页、助手、购药共用同一会话状态', () => {
    saveSelection({ hospitalId: 3, patientId: 2001 });

    expect(getMinePatientId()).toBe(2001);
    saveMinePatientId(2002);
    expect(getSelection()).toEqual({ hospitalId: 3, patientId: 2002 });
  });

  it('全局选择无效时统一回退本人，并在退出时清除', () => {
    const members = [{ patientId: 2001, relation: 'SELF' }, { patientId: 2002, relation: 'CHILD' }];

    expect(resolveMinePatientId(members, 9999)).toBe(2001);
    clearSelection();
    expect(getMinePatientId()).toBeUndefined();
  });
});
