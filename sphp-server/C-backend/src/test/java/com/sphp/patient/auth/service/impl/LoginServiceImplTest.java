package com.sphp.patient.auth.service.impl;

import com.sphp.patient.auth.config.CAuthProperties;
import com.sphp.patient.auth.vo.CaptchaVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * C端登录服务单元测试。
 */
@ExtendWith(MockitoExtension.class)
class LoginServiceImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private LoginServiceImpl loginService;

    /**
     * 初始化验证码测试所需依赖。
     */
    @BeforeEach
    void setUp() {
        CAuthProperties properties = new CAuthProperties();
        properties.setCaptchaExpiration(120);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        loginService = new LoginServiceImpl(properties, redisTemplate);
    }

    /**
     * 验证图形验证码只以 BCrypt 摘要写入 Redis 并设置 120 秒 TTL。
     */
    @Test
    void createCaptchaStoresHashWithTtl() {
        CaptchaVO captcha = loginService.createCaptcha();

        assertTrue(captcha.getChallengeId().startsWith("cap_"));
        assertTrue(captcha.getImageBase64().startsWith("data:image/png;base64,"));
        assertEquals(120, captcha.getExpireSeconds());
        verify(valueOperations).set(
                eq("cend:captcha:" + captcha.getChallengeId()),
                argThat(value -> value != null && value.startsWith("$2")),
                eq(Duration.ofSeconds(120))
        );
    }
}
