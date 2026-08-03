package com.sphp.admin.prescription.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 处方列表项 VO（系分 §5.6.2）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "处方列表项")
public class PrescriptionListVO {

    @Schema(description = "处方 ID")
    private Long id;

    @Schema(description = "问诊记录 ID")
    private Long consultId;

    @Schema(description = "医生姓名")
    private String doctorName;

    @Schema(description = "患者姓名")
    private String patientName;

    @Schema(description = "处方状态")
    private String status;

    @Schema(description = "药品项数")
    private Integer itemCount;

    @Schema(description = "签发时间")
    private OffsetDateTime issuedAt;
}