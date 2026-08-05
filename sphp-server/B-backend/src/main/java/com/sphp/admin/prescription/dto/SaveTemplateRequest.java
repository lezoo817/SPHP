package com.sphp.admin.prescription.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 模板保存请求（系分 §5.6.7）。
 */
@Data
@Schema(description = "保存模板请求")
public class SaveTemplateRequest {

    @Schema(description = "模板名称", example = "高血压常规用药")
    private String name;

    @Schema(description = "关联科室 ID，空表示全院通用", example = "10")
    private Long deptId;

    @Schema(description = "药品明细")
    private List<ItemDTO> items;

    @Data
    @Schema(description = "模板药品项")
    public static class ItemDTO {
        @Schema(description = "药品 ID")
        private Long drugId;
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
        @Schema(description = "数量单位（盒/瓶/剂），默认盒")
        private String quantityUnit;
    }
}