package com.sphp.admin.patient.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 随访计划 VO。
 *
 * @author lezoo17
 * @since 2026-08-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "随访计划")
public class FollowUpPlanVO {

    @Schema(description = "随访计划ID")
    private Long id;

    @Schema(description = "随访类型")
    private String followUpType;

    @Schema(description = "随访内容")
    private String content;

    @Schema(description = "到期时间")
    private OffsetDateTime dueAt;

    @Schema(description = "状态：PENDING_CONFIRM / CONFIRMED / COMPLETED / CANCELLED")
    private String status;

    @Schema(description = "创建时间")
    private OffsetDateTime createdAt;
}