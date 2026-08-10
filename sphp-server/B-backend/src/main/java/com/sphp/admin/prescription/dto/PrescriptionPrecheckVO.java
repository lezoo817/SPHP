package com.sphp.admin.prescription.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 处方风险预检结果 VO。
 *
 * <p>开方过程实时预检的返回体。单药规则（过敏/禁忌/高危药品）的警告带
 * {@code drugId}/{@code drugName}，前端按药品行归属展示；跨药品规则
 * （重复用药）的警告 {@code drugId} 为空，前端置顶汇总。
 *
 * @author lezoo17
 * @since 2026-08-10
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "处方风险预检结果")
public class PrescriptionPrecheckVO {

    @Schema(description = "命中风险清单（可能为空）")
    private List<RiskWarningVO> warnings;
}
