import { describe, expect, it } from 'vitest';
import { resolveConsultationAgentReturnState, resolveConsultationId } from './agent-consultation';

describe('Agent 在线问诊跳转参数', () => {
  it('从预问诊确认结果或既有消息卡片中获取问诊记录 ID', () => {
    expect(resolveConsultationId({ data: { consultationId: 101 } })).toBe(101);
    expect(resolveConsultationId({ messageId: 201 }, 101)).toBe(101);
  });

  it('拒绝非法问诊记录 ID，避免错误跳转聊天页', () => {
    expect(resolveConsultationId({ data: { consultationId: 0 } })).toBeUndefined();
    expect(resolveConsultationId({}, 'invalid')).toBeUndefined();
  });

  it('只接受最小化且合法的 AI 会话返回状态', () => {
    expect(resolveConsultationAgentReturnState({ sessionId: 'session-1', from: '/home' })).toEqual({
      sessionId: 'session-1',
      from: '/home',
    });
    expect(resolveConsultationAgentReturnState({ sessionId: '', from: '/home' })).toBeUndefined();
    expect(resolveConsultationAgentReturnState({ sessionId: 'session-1', from: 'https://bad.example' })).toBeUndefined();
  });
});
