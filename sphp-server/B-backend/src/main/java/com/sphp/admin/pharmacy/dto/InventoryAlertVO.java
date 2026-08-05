package com.sphp.admin.pharmacy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 低库存预警项。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "低库存预警项")
public class InventoryAlertVO {
    @Schema(description = "库存记录ID")
    private Long id;

    @Schema(description = "药房ID")
    private Long pharmacyId;

    @Schema(description = "药房名称")
    private String pharmacyName;

    @Schema(description = "药品ID")
    private Long drugId;

    @Schema(description = "药品名称")
    private String drugName;

    @Schema(description = "规格")
    private String specification;

    @Schema(description = "可售库存")
    private Integer availableCount;

    @Schema(description = "锁定库存")
    private Integer lockedCount;

    @Schema(description = "安全库存")
    private Integer safetyStock;

    @Schema(description = "单价（分）")
    private Integer unitPriceCent;

    @Schema(description = "库存状态：ALERT / LOW")
    private String status;
}
