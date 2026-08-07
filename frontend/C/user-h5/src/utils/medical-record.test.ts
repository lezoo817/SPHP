import { describe, expect, it } from 'vitest';
import { buildMedicalRecordInterpretationAgentState } from './medical-record';

describe('病历 AI 解读跳转状态', () => {
  it('只传来源地址和真实病历 ID，不携带病历正文或健康档案', () => {
    expect(buildMedicalRecordInterpretationAgentState('/medical-records/1001?source=reports', 1001)).toEqual({
      from: '/medical-records/1001?source=reports',
      presetAction: { type: 'interpret_medical_record', consultId: 1001 },
    });
  });
});
