package com.sphp.patient.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * C端退出登录请求。
 */
@Getter
@Setter
@NoArgsConstructor
public class LogoutRequest {

    /** 当前 Access Token 绑定的 Refresh Token 原文 */
    @NotBlank(message = "刷新令牌不能为空")
    private String refreshToken;
}
