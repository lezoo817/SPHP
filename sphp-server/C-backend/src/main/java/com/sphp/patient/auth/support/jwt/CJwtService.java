package com.sphp.patient.auth.support.jwt;

import com.sphp.patient.auth.config.CJwtProperties;
import com.sphp.patient.auth.exception.CAuthException;
import com.sphp.patient.common.constant.CAuthConstant;
import com.sphp.shared.common.enums.ErrorCodeEnum;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Date;

import static com.sphp.patient.common.constant.CAuthConstant.*;
import static com.sphp.shared.common.enums.ErrorCodeEnum.UNAUTHORIZED;

/**
 * C端 Access Token 签发与解析服务。
 */
@Component
@RequiredArgsConstructor
public class CJwtService {

    /** 东八区时间输出区域 */
    private static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");

    private final CJwtProperties properties;

    /**
     * 签发绑定刷新会话的 C端 Access Token。
     *
     * @param userId C端用户 ID
     * @param account 登录账号
     * @param sessionHash 刷新会话摘要
     * @return JWT 字符串
     */
    public String issueAccessToken(Long userId, String account, String sessionHash) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(properties.getExpiration());
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(ACCOUNT_CLAIM, account)
                .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
                .claim(SESSION_HASH_CLAIM, sessionHash)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey())
                .compact();
    }

    /**
     * 校验并解析 C端 Access Token。
     *
     * @param token JWT 字符串
     * @return 已验证的令牌声明
     * @throws CAuthException Token 过期、签名错误或类型不匹配时抛出
     */
    public CJwtClaims parseAccessToken(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(signingKey()).build()
                    .parseSignedClaims(token).getPayload();
            if (!ACCESS_TOKEN_TYPE.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
                throw unauthorized("令牌类型无效");
            }
            return new CJwtClaims(
                    Long.valueOf(claims.getSubject()),
                    claims.get(ACCOUNT_CLAIM, String.class),
                    OffsetDateTime.ofInstant(claims.getExpiration().toInstant(), CHINA_ZONE),
                    claims.get(SESSION_HASH_CLAIM, String.class)
            );
        } catch (CAuthException e) {
            throw e;
        } catch (JwtException | IllegalArgumentException e) {
            throw unauthorized("Token无效或已过期");
        }
    }

    /**
     * 创建 C端 JWT 签名密钥。
     *
     * @return HMAC 签名密钥
     */
    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 创建未授权异常。
     *
     * @param message 用户可读提示
     * @return 未授权异常
     */
    private CAuthException unauthorized(String message) {
        return new CAuthException(UNAUTHORIZED, HttpStatus.UNAUTHORIZED, message);
    }
}
