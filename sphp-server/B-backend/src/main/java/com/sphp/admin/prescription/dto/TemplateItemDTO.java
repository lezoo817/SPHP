package com.sphp.admin.prescription.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 模板药品项 DTO（对应 prescription_template.items JSONB 结构）。
 */
@Data
@Schema(description = "模板药品项")
public class TemplateItemDTO {

    @Schema(description = "药品 ID")
    private Long drugId;

    @Schema(description = "药品名称（冗余，便于展示）")
    private String drugName;

    @Schema(description = "用量（QD/BID/TID 等）")
    private String dosage;

    @Schema(description = "频次说明")
    private String frequency;

    @Schema(description = "用法（口服/外用等）")
    private String usageMethod;

    @Schema(description = "天数")
    private Integer days;

    @Schema(description = "数量")
    private Integer quantity;
}