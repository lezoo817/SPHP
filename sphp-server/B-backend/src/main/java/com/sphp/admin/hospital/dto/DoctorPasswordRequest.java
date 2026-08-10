package com.sphp.admin.hospital.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 重置医生登录密码请求。
 *
 * <p>管理员直接重置，不校验旧密码，不要求短信/MFA。
 *
 * @author lezoo17
 * @since 2026-08-10
 */
@Data
@Schema(description = "重置医生登录密码请求")
public class DoctorPasswordRequest {

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度需为6-64位")
    @Schema(description = "新密码，6-64位，jBCrypt哈希入库", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;
}
