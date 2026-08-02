package com.sphp.patient.triage.vo;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 导诊评估响应。
 */
@Getter
@Builder
public class TriageAssessmentVO {

    /** 导诊评估记录 ID。 */
    private final Long assessmentId;

    /** 评估紧急程度。 */
    private final String urgency;

    /** 推荐科室列表。 */
    private final List<RecommendedDepartment> recommendedDepartments;

    /** 非诊断性免责声明。 */
    private final String disclaimer;

    /**
     * 推荐科室展示项。
     */
    @Getter
    @Builder
    public static class RecommendedDepartment {

        /** 科室 ID。 */
        private final Long id;

        /** 科室名称。 */
        private final String name;

        /** 命中规则提供的推荐原因。 */
        private final String reason;
    }
}
