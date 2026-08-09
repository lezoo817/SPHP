package com.sphp.admin.doctor.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 接诊台补录患者过敏史请求。
 *
 * @author lezoo17
 * @since 2026-08-09
 */
@Data
@Schema(description = "接诊台补录过敏史请求")
public class AllergyCreateRequest {

    @NotBlank(message = "过敏原不能为空")
    @Size(max = 200, message = "过敏原不能超过200字符")
    @Schema(description = "过敏原", example = "青霉素")
    private String allergen;

    @Size(max = 500, message = "反应描述不能超过500字符")
    @Schema(description = "反应描述", example = "皮疹伴瘙痒")
    private String reaction;

    @NotBlank(message = "严重程度不能为空")
    @Pattern(regexp = "MILD|MODERATE|SEVERE", message = "严重程度仅支持 MILD / MODERATE / SEVERE")
    @Schema(description = "严重程度：MILD 轻度 / MODERATE 中度 / SEVERE 重度", example = "MODERATE")
    private String severity;
}
