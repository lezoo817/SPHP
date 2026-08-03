package com.sphp.admin.prescription.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 处方风险警告 VO（系分 §5.6.1）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "处方风险警告")
public class RiskWarningVO {

    @Schema(description = "风险级别：WARNING / AUDIT")
    private String level;

    @Schema(description = "触发规则名称")
    private String rule;

    @Schema(description = "提示信息")
    private String message;
}