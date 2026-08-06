package com.sphp.admin.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 刷新令牌请求参数。
 *
 * <p>refreshToken 由 {@code /b/auth/login} 或 {@code /b/auth/token/refresh} 返回，
 * 一次性使用，刷新成功后旧令牌立即吊销。
 */
@Data
@Schema(description = "刷新令牌请求")
public class RefreshTokenRequest {

    @NotBlank(message = "刷新令牌不能为空")
    @Schema(description = "刷新令牌（登录 / 刷新接口返回的 refreshToken 原文）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String refreshToken;
}
