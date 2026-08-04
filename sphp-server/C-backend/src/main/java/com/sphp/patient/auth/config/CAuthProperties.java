package com.sphp.patient.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * C端认证业务配置。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "sphp.auth")
public class CAuthProperties {

    /** 图形验证码有效期，单位为秒 */
    private long captchaExpiration;

    /** 登录失败最大次数 */
    private int loginMaxFailures;

    /** 登录失败锁定时间，单位为秒 */
    private long loginLockSeconds;

    /** Refresh Token 有效期，单位为秒 */
    private long refreshTokenExpiration;
}
