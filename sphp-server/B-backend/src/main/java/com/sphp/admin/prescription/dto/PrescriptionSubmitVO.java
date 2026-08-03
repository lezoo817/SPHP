package com.sphp.admin.prescription.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 处方提交响应 VO（系分 §5.6.1）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "处方提交响应")
public class PrescriptionSubmitVO {

    @Schema(description = "处方 ID")
    private Long id;

    @Schema(description = "处方状态：APPROVED / SUBMITTED")
    private String status;

    @Schema(description = "是否需要人工审核")
    private Boolean auditRequired;

    @Schema(description = "风险警告列表")
    private List<RiskWarningVO> riskWarnings;
}