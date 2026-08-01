package com.sphp.patient.auth.support.context;

import java.time.OffsetDateTime;

/**
 * 当前请求的 C端用户身份。
 *
 * @param userId C端用户 ID
 * @param account 登录账号
 * @param tokenExpiresAt Access Token 过期时间
 * @param sessionHash 当前刷新会话摘要
 */
public record CUserPrincipal(Long userId, String account, OffsetDateTime tokenExpiresAt, String sessionHash) {
}
