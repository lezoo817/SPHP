package com.sphp.admin.prescription.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 处方提交请求体。
 *
 * @author lezoo17
 * @since 2026-08-09
 */
@Data
@Schema(description = "处方提交请求")
public class PrescriptionSubmitRequest {

    @NotNull(message = "问诊记录 ID 不能为空")
    @Schema(description = "问诊记录 ID")
    private Long consultId;

    @NotEmpty(message = "处方明细至少 1 项")
    @Size(min = 1, message = "处方明细至少 1 项")
    @Valid
    @Schema(description = "处方明细列表")
    private List<ItemDTO> items;

    @Data
    @Schema(description = "处方明细项")
    public static class ItemDTO {

        @NotNull(message = "药品 ID 不能为空")
        @Schema(description = "药品 ID")
        private Long drugId;

        @NotBlank(message = "用量不能为空")
        @Size(max = 50, message = "用量不能超过50字符")
        @Schema(description = "用量（如 QD / BID / TID）", maxLength = 50)
        private String dosage;

        @Size(max = 50, message = "频次不能超过50字符")
        @Schema(description = "频次描述", maxLength = 50)
        private String frequency;

        @NotBlank(message = "用法不能为空")
        @Size(max = 50, message = "用法不能超过50字符")
        @Schema(description = "用法（如口服 / 外用 / 注射）", maxLength = 50)
        private String usageMethod;

        @NotNull(message = "用药天数不能为空")
        @Schema(description = "用药天数")
        private Integer days;

        @NotNull(message = "数量不能为空")
        @Schema(description = "数量（盒/瓶）")
        private Integer quantity;
    }
}