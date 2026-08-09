package com.sphp.admin.patient.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 患者就诊记录 VO。
 *
 * @author lezoo17
 * @since 2026-08-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "患者就诊记录")
public class PatientVisitVO {

    @Schema(description = "问诊记录ID")
    private Long consultId;

    @Schema(description = "就诊日期")
    private LocalDate visitDate;

    @Schema(description = "医生姓名")
    private String doctorName;

    @Schema(description = "科室名称")
    private String deptName;

    @Schema(description = "诊断摘要")
    private String summary;

    @Schema(description = "状态：PENDING / IN_PROGRESS / COMPLETED / NO_SHOW")
    private String status;

    @Schema(description = "就诊时间")
    private OffsetDateTime createdAt;
}