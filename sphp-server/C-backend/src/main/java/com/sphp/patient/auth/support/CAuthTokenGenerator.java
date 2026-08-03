package com.sphp.patient.auth.support;

import com.sphp.patient.common.constant.CAuthConstant;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

import static com.sphp.patient.common.constant.CAuthConstant.CAPTCHA_CHALLENGE_PREFIX;
import static com.sphp.patient.common.constant.CAuthConstant.REFRESH_TOKEN_PREFIX;

/**
 * C端认证随机标识生成器。
 */
public final class CAuthTokenGenerator {

    /** 安全随机源 */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * 生成不透明刷新令牌。
     *
     * @return 带 rt_ 前缀的刷新令牌原文
     */
    public static String generateRefreshToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return REFRESH_TOKEN_PREFIX
                + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 生成图形验证码挑战标识。
     *
     * @return 带 cap_ 前缀的挑战标识
     */
    public static String generateCaptchaChallengeId() {
        return CAPTCHA_CHALLENGE_PREFIX + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 防止工具类被实例化。
     */
    private CAuthTokenGenerator() {
    }
}
