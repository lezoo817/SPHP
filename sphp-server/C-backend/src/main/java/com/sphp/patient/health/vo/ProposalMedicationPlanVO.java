package com.sphp.patient.health.vo;
import lombok.*;
import java.time.OffsetDateTime;

/** 用药计划响应。 */
@Getter
@Builder
public class ProposalMedicationPlanVO {
    // 用药计划 ID
    private final Long id;
    // 药品名称
    private final String drugName;
    // 用药剂量
    private final String dosage;
    // 用药频率
    private final String frequency;
    // 下次提醒时间
    private final OffsetDateTime nextReminderAt;
    // 用药计划状态
    private final String status;

}
