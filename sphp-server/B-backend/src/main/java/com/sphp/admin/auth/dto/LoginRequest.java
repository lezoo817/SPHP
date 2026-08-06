package com.sphp.admin.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求参数。
 *
 * <p>账号命名遵循 {@code b_user.account}（全院唯一）；密码以明文传输，落库前由服务端 BCrypt 加盐哈希。
 */
@Data
@Schema(description = "登录请求")
public class LoginRequest {

    @NotBlank(message = "登录账号不能为空")
    @Schema(description = "登录账号（全院唯一）", example = "admin", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @NotBlank(message = "登录密码不能为空")
    @Schema(description = "登录密码（明文传输，服务端 BCrypt 校验）", example = "Admin@123", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;
}
