package com.sphp.admin.pharmacy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 新增药品请求。 */
@Data
@Schema(description = "新增药品请求")
public class DrugCreateRequest {
    @Schema(description = "药品名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "药品名称不能为空")
    private String name;

    @Schema(description = "规格", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "规格不能为空")
    private String specification;

    @Schema(description = "单位，默认盒")
    private String unit;

    @Schema(description = "适应症")
    private String indication;

    @Schema(description = "禁忌症（供处方风险拦截器做禁忌匹配，如：活动性消化道溃疡患者禁用）")
    private String contraindication;

    @Schema(description = "生产厂家")
    private String manufacturer;

    @Schema(description = "批准文号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "批准文号不能为空")
    private String approvalNumber;

    @Schema(description = "状态：ENABLED / DISABLED，默认 ENABLED")
    private String status;
}
