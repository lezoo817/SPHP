package com.sphp.patient.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * C端刷新令牌请求。
 */
@Getter
@Setter
@NoArgsConstructor
public class RefreshTokenRequest {

    /** 登录成功或上次刷新取得的 Refresh Token 原文 */
    @NotBlank(message = "刷新令牌不能为空")
    private String refreshToken;
}
