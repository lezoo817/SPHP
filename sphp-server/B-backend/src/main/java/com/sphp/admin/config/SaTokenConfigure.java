package com.sphp.admin.config;

import cn.dev33.satoken.jwt.StpLogicJwtForStateless;
import cn.dev33.satoken.stp.StpLogic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * B端 Sa-Token 配置：注册无状态 JWT 签发逻辑。
 *
 * <p>选用 {@link StpLogicJwtForStateless}：accessToken 为自包含 JWT，应用重启不失效；
 * 退出登录的安全兜底由「吊销刷新令牌」承担（accessToken 7200s 内自过期）。
 */
@Configuration
public class SaTokenConfigure {

    @Bean
    public StpLogic getStpLogicJwt() {
        return new StpLogicJwtForStateless();
    }
}
