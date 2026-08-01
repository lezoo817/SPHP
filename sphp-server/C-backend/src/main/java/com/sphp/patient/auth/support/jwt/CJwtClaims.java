package com.sphp.patient.auth.support.jwt;

import java.time.OffsetDateTime;

/**
 * 已验证的 C端访问令牌声明。
 *
 * @param userId C端用户 ID
 * @param account 登录账号
 * @param expiresAt 令牌过期时间
 * @param sessionHash 刷新会话摘要
 */
public record CJwtClaims(Long userId, String account, OffsetDateTime expiresAt, String sessionHash) {
}
