package com.sphp.patient.health.vo;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.List;

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

    // 是否由用户手动开启提醒
    private final boolean reminderEnabled;

    // 根据处方频次固定生成的每日提醒时刻
    private final List<String> reminderTimes;

    // 用药计划状态
    private final String status;

}
