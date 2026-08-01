package com.sphp.patient.auth.service.impl;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import com.sphp.patient.auth.config.CAuthProperties;
import com.sphp.patient.auth.service.LoginService;
import com.sphp.patient.auth.support.CAuthTokenGenerator;
import com.sphp.patient.auth.vo.CaptchaVO;
import com.sphp.patient.common.constant.CAuthConstant;
import lombok.RequiredArgsConstructor;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;

/**
 * C端登录注册服务实现。
 */
@Service
@RequiredArgsConstructor
public class LoginServiceImpl implements LoginService {

    /** 验证码图片宽度 */
    private static final int CAPTCHA_WIDTH = 120;
    /** 验证码图片高度 */
    private static final int CAPTCHA_HEIGHT = 40;
    /** 验证码字符数量 */
    private static final int CAPTCHA_LENGTH = 4;
    /** 验证码干扰线数量 */
    private static final int CAPTCHA_LINE_COUNT = 30;

    private final CAuthProperties authProperties;
    private final StringRedisTemplate redisTemplate;

    /**
     * 生成一次性图形验证码并保存 BCrypt 摘要。
     *
     * @return 图形验证码信息
     */
    @Override
    public CaptchaVO createCaptcha() {
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(
                CAPTCHA_WIDTH, CAPTCHA_HEIGHT, CAPTCHA_LENGTH, CAPTCHA_LINE_COUNT);
        String challengeId = CAuthTokenGenerator.generateCaptchaChallengeId();
        String captchaHash = BCrypt.hashpw(
                captcha.getCode().toUpperCase(Locale.ROOT), BCrypt.gensalt());

        // Redis 仅保存短期摘要，避免验证码原文泄漏或被重复使用
        redisTemplate.opsForValue().set(
                CAuthConstant.CAPTCHA_KEY_PREFIX + challengeId,
                captchaHash,
                Duration.ofSeconds(authProperties.getCaptchaExpiration())
        );
        return CaptchaVO.builder()
                .challengeId(challengeId)
                .imageBase64(captcha.getImageBase64Data())
                .expireSeconds(authProperties.getCaptchaExpiration())
                .build();
    }
}
