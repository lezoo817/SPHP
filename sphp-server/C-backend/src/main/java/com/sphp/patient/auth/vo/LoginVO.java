package com.sphp.patient.auth.vo;

import lombok.Builder;
import lombok.Getter;

/**
 * C端账号密码登录响应。
 */
@Getter
@Builder
public class LoginVO {

    /** C端访问令牌 */
    private final String accessToken;

    /** 一次性返回客户端的刷新令牌原文 */
    private final String refreshToken;

    /** Access Token 有效期秒数 */
    private final long expiresIn;

    /** 登录用户摘要 */
    private final LoginUserVO user;
}
