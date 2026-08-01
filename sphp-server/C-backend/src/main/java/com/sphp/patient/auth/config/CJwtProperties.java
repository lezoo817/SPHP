package com.sphp.patient.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * C端患者 JWT 配置。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "sphp.jwt.patient")
public class CJwtProperties {

    /** C端独立 JWT 签名密钥 */
    private String secret;
    /** Access Token 有效期，单位为秒 */
    private long expiration;
}
