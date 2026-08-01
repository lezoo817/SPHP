package com.sphp.admin.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "登录响应")
public class LoginVO {

    @Schema(description = "访问令牌（JWT，有效期 expiresIn 秒）")
    private String accessToken;

    @Schema(description = "刷新令牌（用于续期，仅本次返回原文）")
    private String refreshToken;

    @Schema(description = "访问令牌有效期（秒）", example = "7200")
    private Long expiresIn;

    @Schema(description = "用户信息")
    private UserInfoVO userInfo;
}
