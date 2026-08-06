package com.sphp.admin.hospital.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 启用/停用/暂停医生请求。
 */
@Data
@Schema(description = "启用/停用/暂停医生请求")
public class DoctorStatusRequest {

    @NotBlank(message = "状态不能为空")
    @Pattern(regexp = "ENABLED|DISABLED|SUSPENDED", message = "状态仅支持 ENABLED / DISABLED / SUSPENDED")
    @Schema(description = "状态：ENABLED / DISABLED / SUSPENDED", example = "DISABLED",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String status;
}
