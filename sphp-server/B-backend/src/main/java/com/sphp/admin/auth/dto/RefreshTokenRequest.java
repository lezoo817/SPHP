package com.sphp.admin.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 刷新令牌请求参数。
 */
@Data
@Schema(description = "刷新令牌请求")
public class RefreshTokenRequest {

    @NotBlank(message = "刷新令牌不能为空")
    @Schema(description = "刷新令牌（登录/刷新接口返回的 refreshToken）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String refreshToken;
}
