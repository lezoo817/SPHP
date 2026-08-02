package com.sphp.patient.consultation.vo;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * 处方解读响应。
 */
@Getter
@Builder
public class PrescriptionInterpretationVO {

    /** 处方 ID。 */
    private final Long prescriptionId;

    /** 已生成的处方解读内容。 */
    private final String content;

    /** 解读免责声明。 */
    private final String disclaimer;

    /** 解读生成时间。 */
    private final OffsetDateTime generatedAt;
}
