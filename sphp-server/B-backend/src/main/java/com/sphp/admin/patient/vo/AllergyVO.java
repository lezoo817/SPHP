package com.sphp.admin.patient.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 过敏史 VO。
 *
 * @author lezoo17
 * @since 2026-08-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "过敏史")
public class AllergyVO {

    @Schema(description = "过敏史ID")
    private Long id;

    @Schema(description = "过敏原")
    private String allergen;

    @Schema(description = "反应描述")
    private String reaction;

    @Schema(description = "严重程度：MILD / MODERATE / SEVERE")
    private String severity;
}