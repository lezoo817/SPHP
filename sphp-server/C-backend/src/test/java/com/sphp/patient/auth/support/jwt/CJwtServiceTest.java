package com.sphp.patient.auth.support.jwt;

import com.sphp.patient.auth.config.CJwtProperties;
import com.sphp.patient.auth.exception.CAuthException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * C端 JWT 服务单元测试。
 */
class CJwtServiceTest {

    /**
     * 验证签发的 C 端访问令牌能够恢复最小用户上下文。
     */
    @Test
    void issueAndParseAccessToken() {
        CJwtService jwtService = new CJwtService(properties("c-patient-test-secret-key-at-least-32-bytes", 7200));

        String token = jwtService.issueAccessToken(10001L, "patient_zhangsan", "session-hash");
        CJwtClaims claims = jwtService.parseAccessToken(token);

        assertEquals(10001L, claims.userId());
        assertEquals("patient_zhangsan", claims.account());
        assertEquals("session-hash", claims.sessionHash());
    }

    /**
     * 验证不同签名密钥签发的 Token 不能跨端解析。
     */
    @Test
    void rejectTokenSignedByAnotherSecret() {
        CJwtService cJwtService = new CJwtService(properties("c-patient-test-secret-key-at-least-32-bytes", 7200));
        CJwtService anotherJwtService = new CJwtService(properties("b-admin-test-secret-key-at-least-32-bytes-x", 7200));
        String token = anotherJwtService.issueAccessToken(20001L, "admin", "another-session");

        assertThrows(CAuthException.class, () -> cJwtService.parseAccessToken(token));
    }

    /**
     * 创建测试用 JWT 配置。
     *
     * @param secret 签名密钥
     * @param expirationSeconds Access Token 有效期秒数
     * @return JWT 配置
     */
    private static CJwtProperties properties(String secret, long expirationSeconds) {
        CJwtProperties properties = new CJwtProperties();
        properties.setSecret(secret);
        properties.setExpiration(expirationSeconds);
        return properties;
    }
}
