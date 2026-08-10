package com.sphp.admin.prescription.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 处方风险警告 VO。
 *
 * <p>既用于提交后的风险规则快照（落库 {@code prescription.risk_warnings}），
 * 也用于开方过程实时预检的返回值。单药规则（过敏/禁忌/高危药品）会带
 * {@code drugId}/{@code drugName} 便于前端按药品行归属展示；跨药品规则
 * （重复用药）这两个字段为空，前端置顶汇总。
 *
 * @author lezoo17
 * @since 2026-08-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "处方风险警告")
public class RiskWarningVO {

    @Schema(description = "风险级别：ERROR（红线，禁止提交）/ WARNING（提示，处方生效）/ AUDIT（审核，处方进待审核队列）",
            example = "ERROR")
    private String level;

    @Schema(description = "触发规则名称")
    private String rule;

    @Schema(description = "提示信息")
    private String message;

    @Schema(description = "命中药品 ID（重复用药等跨药品规则为空）")
    private Long drugId;

    @Schema(description = "命中药品名称")
    private String drugName;

    @Schema(description = "命中来源（如：患者过敏史「青霉素」/ 既往史「糖尿病」）")
    private String source;
}