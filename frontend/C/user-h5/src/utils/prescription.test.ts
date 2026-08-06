import { describe, expect, it } from 'vitest';
import { buildPrescriptionInterpretationAgentState } from './prescription';

describe('处方 AI 解读跳转状态', () => {
  it('只传来源地址和真实处方 ID，不携带处方正文', () => {
    expect(buildPrescriptionInterpretationAgentState('/pharmacy/prescription/1001?patientId=9', 1001)).toEqual({
      from: '/pharmacy/prescription/1001?patientId=9',
      presetAction: { type: 'interpret_prescription', prescriptionId: 1001 },
    });
  });
});
