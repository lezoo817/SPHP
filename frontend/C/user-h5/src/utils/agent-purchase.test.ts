import { describe, expect, it } from 'vitest';
import { resolveDrugOrderAgentReturnState, resolveDrugOrderPaymentResult } from './agent-purchase';

describe('Agent 购药跳转参数', () => {
  it('提取 Java 下单结果中的订单和支付单 ID', () => {
    expect(resolveDrugOrderPaymentResult({ drugOrderId: 101, paymentId: 202 })).toEqual({
      drugOrderId: 101,
      paymentId: 202,
    });
    expect(resolveDrugOrderPaymentResult({ data: { drugOrderId: 101, paymentId: 202 } })).toEqual({
      drugOrderId: 101,
      paymentId: 202,
    });
  });

  it('拒绝不完整或非法的下单结果，避免错误跳转支付页', () => {
    expect(resolveDrugOrderPaymentResult({ drugOrderId: 101 })).toBeUndefined();
    expect(resolveDrugOrderPaymentResult({ drugOrderId: 0, paymentId: 202 })).toBeUndefined();
  });

  it('只接受最小化且合法的 AI 会话返回状态', () => {
    expect(resolveDrugOrderAgentReturnState({ sessionId: 'session-1', from: '/assistant/prescription/1' })).toEqual({
      sessionId: 'session-1',
      from: '/assistant/prescription/1',
    });
    expect(resolveDrugOrderAgentReturnState({ sessionId: '', from: '/pharmacy' })).toBeUndefined();
    expect(resolveDrugOrderAgentReturnState({ sessionId: 'session-1', from: 'https://bad.example' })).toBeUndefined();
  });
});
