package com.sphp.admin.pharmacy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 释放锁定库存请求。
 *
 * @author lezoo17
 * @since 2026-08-10
 */
@Data
@Schema(description = "释放锁定库存请求")
public class InventoryUnlockRequest {
    @Schema(description = "购药订单ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "购药订单ID不能为空")
    private Long drugOrderId;

    @Schema(description = "释放原因")
    private String reason;
}
