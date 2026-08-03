package com.sphp.admin.patient.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 用药计划 VO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用药计划")
public class MedicationPlanVO {

    @Schema(description = "用药计划ID")
    private Long id;

    @Schema(description = "药品名称")
    private String drugName;

    @Schema(description = "用量")
    private String dosage;

    @Schema(description = "频次")
    private String frequency;

    @Schema(description = "用法")
    private String usageMethod;

    @Schema(description = "状态：ACTIVE / PAUSED / COMPLETED")
    private String status;

    @Schema(description = "下次提醒时间")
    private OffsetDateTime nextRemindAt;

    @Schema(description = "创建时间")
    private OffsetDateTime createdAt;
}