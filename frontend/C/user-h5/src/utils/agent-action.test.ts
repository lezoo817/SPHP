import { describe, expect, it } from 'vitest';
import { resolveAgentActionRequest } from './agent-action';

describe('Agent 业务交互卡预设请求', () => {
  it('仅将合法订单 ID 转换为收货后提醒授权预设', () => {
    expect(resolveAgentActionRequest({
      actionType: 'authorize_drug_order_reminder_after_receipt',
      arguments: { drug_order_id: 101 },
    }, { page: 'pharmacy' })).toEqual({
      content: '请设置本订单收货后自动开启用药提醒。',
      context: {
        page: 'pharmacy',
        preset_action: 'authorize_drug_order_reminder_after_receipt',
        drug_order_id: 101,
      },
    });
  });

  it('拒绝非法订单 ID 和未知交互动作', () => {
    expect(resolveAgentActionRequest({
      actionType: 'authorize_drug_order_reminder_after_receipt',
      arguments: { drug_order_id: 0 },
    })).toBeUndefined();
    expect(resolveAgentActionRequest({
      actionType: 'unknown',
      arguments: { drug_order_id: 101 },
    })).toBeUndefined();
  });
});
