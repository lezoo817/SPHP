package com.sphp.admin.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 退出登录响应。
 *
 * <p>仅表示 refreshToken 已吊销，accessToken 为无状态 JWT，等待自然过期（详见
 * {@link com.sphp.admin.auth.service.impl.AuthServiceImpl} 类注释）。
 *
 * @author lezoo17
 * @since 2026-08-10
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "退出登录响应")
public class LogoutVO {

    @Schema(description = "是否已退出（true 表示 refreshToken 已吊销）", example = "true")
    private Boolean loggedOut;
}
