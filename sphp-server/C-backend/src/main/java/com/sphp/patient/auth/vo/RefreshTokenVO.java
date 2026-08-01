package com.sphp.patient.auth.vo;

import lombok.Builder;
import lombok.Getter;

/**
 * C端刷新令牌轮换响应。
 */
@Getter
@Builder
public class RefreshTokenVO {

    /** 新 C端访问令牌 */
    private final String accessToken;
    /** 新刷新令牌原文 */
    private final String refreshToken;
    /** Access Token 有效期秒数 */
    private final long expiresIn;
}
