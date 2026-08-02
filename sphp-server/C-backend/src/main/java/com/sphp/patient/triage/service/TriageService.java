package com.sphp.patient.triage.service;

import com.sphp.patient.triage.dto.TriageAssessmentCreateRequest;
import com.sphp.patient.triage.vo.TriageAssessmentVO;

/**
 * C端症状导诊服务。
 */
public interface TriageService {

    /**
     * 提交症状并生成非诊断性的导诊建议。
     *
     * @param request 导诊评估请求
     * @return 已保存的导诊评估结果
     */
    TriageAssessmentVO triageCreateAssessment(TriageAssessmentCreateRequest request);
}
