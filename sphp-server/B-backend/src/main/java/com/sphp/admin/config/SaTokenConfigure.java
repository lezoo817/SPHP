package com.sphp.admin.config;

import cn.dev33.satoken.jwt.StpLogicJwtForStateless;
import cn.dev33.satoken.stp.StpLogic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * B 端 Sa-Token 配置：注册无状态 JWT 签发逻辑。
 *
 * <p>选用 {@link StpLogicJwtForStateless}：accessToken 为自包含 JWT，应用重启不失效；
 * 退出登录的安全兜底由「吊销刷新令牌」承担（accessToken 在
 * {@code application-dev.yml} 配置的 7200s 内自过期，详见
 * {@link com.sphp.admin.auth.service.impl.AuthServiceImpl}）。accessToken 解析出的
 * 角色与权限码由 {@link StpInterfaceImpl} 动态加载（角色取自 {@code b_user.role}，
 * 字段值由 {@link com.sphp.admin.common.enums.BRoleEnum} 约束）。
 */
@Configuration
public class SaTokenConfigure {

    /**
     * 注册 Sa-Token 无状态 JWT 签发逻辑。
     *
     * <p>无状态模式下，Sa-Token 不再在服务端存储 token-user 映射关系，token 自身即足以
     * 解析登录身份；与之配套的 {@link StpInterfaceImpl} 必须能在仅持有 loginId 的情况下
     * 还原出角色 / 权限码。
     *
     * @return Sa-Token 鉴权逻辑实例（无状态 JWT 实现）
     */
    @Bean
    public StpLogic getStpLogicJwt() {
        return new StpLogicJwtForStateless();
    }
}
