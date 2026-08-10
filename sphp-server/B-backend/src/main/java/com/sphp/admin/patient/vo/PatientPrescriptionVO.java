package com.sphp.admin.patient.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 患者历史处方 VO。
 *
 * @author lezoo17
 * @since 2026-08-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "患者历史处方")
public class PatientPrescriptionVO {

    @Schema(description = "处方ID")
    private Long id;

    @Schema(description = "问诊记录ID")
    private Long consultId;

    @Schema(description = "医生姓名")
    private String doctorName;

    @Schema(description = "状态：DRAFT / SUBMITTED / APPROVED / REJECTED / CANCELLED")
    private String status;

    @Schema(description = "药品数量")
    private Integer itemCount;

    @Schema(description = "签发时间")
    private OffsetDateTime issuedAt;

    @Schema(description = "创建时间")
    private OffsetDateTime createdAt;
}