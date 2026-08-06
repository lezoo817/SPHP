package com.sphp.admin.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应。
 *
 * <p>accessToken 由 Sa-Token 签发的无状态 JWT；refreshToken 仅本次返回原文，客户端需持久化用于续期。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "登录响应")
public class LoginVO {

    @Schema(description = "访问令牌（JWT，有效期见 expiresIn）")
    private String accessToken;

    @Schema(description = "刷新令牌（仅本次返回原文，服务端仅存哈希）")
    private String refreshToken;

    @Schema(description = "访问令牌有效期（秒）", example = "7200")
    private Long expiresIn;

    @Schema(description = "当前登录用户信息")
    private UserInfoVO userInfo;
}
