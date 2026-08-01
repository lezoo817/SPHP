package com.sphp.patient.support.idempotency;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * C端接口幂等配置。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "sphp.idempotency")
public class CIdempotencyProperties {

    /** 成功结果缓存时间，单位为秒 */
    private long successTtlSeconds;
    /** 请求处理中占位时间，单位为秒 */
    private long processingTtlSeconds;
}
