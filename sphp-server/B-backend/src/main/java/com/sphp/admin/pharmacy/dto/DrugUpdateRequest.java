package com.sphp.admin.pharmacy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 编辑药品请求（所有字段均可选）。 */
@Data
@Schema(description = "编辑药品请求")
public class DrugUpdateRequest {
    @Schema(description = "药品名称")
    private String name;

    @Schema(description = "规格")
    private String specification;

    @Schema(description = "单位")
    private String unit;

    @Schema(description = "适应症")
    private String indication;

    @Schema(description = "生产厂家")
    private String manufacturer;

    @Schema(description = "批准文号")
    private String approvalNumber;

    @Schema(description = "状态：ENABLED / DISABLED")
    private String status;
}
