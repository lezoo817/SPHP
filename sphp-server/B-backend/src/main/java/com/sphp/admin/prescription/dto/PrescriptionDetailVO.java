package com.sphp.admin.prescription.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 处方详情 VO（系分 §5.6.3）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "处方详情")
public class PrescriptionDetailVO {

    @Schema(description = "处方 ID")
    private Long id;

    @Schema(description = "问诊记录 ID")
    private Long consultId;

    @Schema(description = "医生信息")
    private DoctorInfo doctor;

    @Schema(description = "患者信息")
    private PatientInfo patient;

    @Schema(description = "处方状态")
    private String status;

    @Schema(description = "是否需要人工审核")
    private Boolean auditRequired;

    @Schema(description = "风险警告列表")
    private List<RiskWarningVO> riskWarnings;

    @Schema(description = "处方明细")
    private List<ItemVO> items;

    @Schema(description = "签发时间")
    private OffsetDateTime issuedAt;

    @Schema(description = "审核时间")
    private OffsetDateTime auditedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "医生信息")
    public static class DoctorInfo {
        private Long id;
        private String name;
        private String title;
        private String deptName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "患者信息")
    public static class PatientInfo {
        private Long id;
        private String name;
        private String gender;
        private LocalDate dateOfBirth;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "处方明细项")
    public static class ItemVO {
        private Long id;
        private Long drugId;
        private String drugName;
        private String specification;
        private String dosage;
        private String frequency;
        private String usageMethod;
        private Integer days;
        private Integer quantity;
    }
}