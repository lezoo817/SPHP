import { describe, expect, it } from 'vitest';
import { buildConsultationDetailPath } from './consultation';

describe('在线问诊详情路径', () => {
  it('从通知消息进入时保留受控的通知页返回路径', () => {
    expect(buildConsultationDetailPath(101, 2001, '/mine/notifications')).toBe('/assistant/consultation/101?patientId=2001&returnTo=%2Fmine%2Fnotifications');
  });

  it('常规入口不携带返回路径参数', () => {
    expect(buildConsultationDetailPath(101, 2001)).toBe('/assistant/consultation/101?patientId=2001');
  });
});
