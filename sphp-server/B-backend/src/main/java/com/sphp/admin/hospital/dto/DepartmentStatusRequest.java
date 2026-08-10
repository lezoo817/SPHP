package com.sphp.admin.hospital.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 启用/停用科室请求。
 *
 * @author lezoo17
 * @since 2026-08-10
 */
@Data
@Schema(description = "启用/停用科室请求")
public class DepartmentStatusRequest {

    @NotBlank(message = "状态不能为空")
    @Pattern(regexp = "ENABLED|DISABLED", message = "状态仅支持 ENABLED / DISABLED")
    @Schema(description = "状态：ENABLED / DISABLED", example = "DISABLED",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String status;
}
