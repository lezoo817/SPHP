package com.sphp.admin.pharmacy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 更新库存请求（所有字段均可选）。 */
@Data
@Schema(description = "更新库存请求")
public class InventoryUpdateRequest {
    @Schema(description = "可售库存")
    private Integer availableCount;

    @Schema(description = "锁定库存")
    private Integer lockedCount;

    @Schema(description = "安全库存")
    private Integer safetyStock;

    @Schema(description = "单价（分）")
    private Integer unitPriceCent;
}
