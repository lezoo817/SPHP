package com.sphp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * Redisson 客户端配置（开发环境降级）。
 *
 * <p>当 {@link org.redisson.spring.starter.RedissonAutoConfigurationV2} 被排除时，
 * 本配置提供可用的 {@link RedissonClient} bean，连接本地 Redis，不阻塞启动。
 */
@Configuration
@ConditionalOnMissingBean(RedissonClient.class)
public class RedissonConfig {

    /** 本地 Redis 连接地址 */
    private static final String REDIS_ADDRESS = "redis://localhost:6379";

    @Bean(destroyMethod = "shutdown")
    @Lazy
    public RedissonClient redissonClient() {
        Config config = new Config();
        SingleServerConfig singleServerConfig = config.useSingleServer()
                .setAddress(REDIS_ADDRESS)
                // 注意：不能设为 0。Redisson 3.27.2 的 doConnect 会用
                // connectTimeout * connectionMinimumIdleSize 作为启动等待超时，
                // 设为 0 时超时为 0ms，导致连接立刻抛 TimeoutException（见 Git 排查记录）。
                .setConnectionMinimumIdleSize(1)
                .setConnectionPoolSize(1)
                .setConnectTimeout(3000)
                .setTimeout(3000)
                .setRetryAttempts(0);
        // 本地 Redis 未设密码，不设置 password
        return Redisson.create(config);
    }
}