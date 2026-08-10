package com.sphp.admin.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 刷新令牌响应。
 *
 * <p>旧 refreshToken 在签发新令牌后立即吊销，保证一次性使用。
 *
 * @author lezoo17
 * @since 2026-08-10
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "刷新令牌响应")
public class RefreshTokenVO {

    @Schema(description = "新的访问令牌（JWT）")
    private String accessToken;

    @Schema(description = "新的刷新令牌（仅本次返回原文，旧令牌已吊销）")
    private String refreshToken;

    @Schema(description = "访问令牌有效期（秒）", example = "7200")
    private Long expiresIn;
}
