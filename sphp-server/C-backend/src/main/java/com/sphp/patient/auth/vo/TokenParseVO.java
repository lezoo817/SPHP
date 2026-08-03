package com.sphp.patient.auth.vo;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * C端 Access Token 最小身份解析结果。
 */
@Getter
@Builder
public class TokenParseVO {

    /** C端用户 ID */
    private final Long userId;

    /** 登录账号 */
    private final String account;

    /** Access Token 过期时间 */
    private final OffsetDateTime tokenExpiresAt;
}
