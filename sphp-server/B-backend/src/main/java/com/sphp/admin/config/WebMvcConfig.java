package com.sphp.admin.config;

import com.sphp.admin.common.UserContextInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * B 端 MVC 配置：注册 Agent 用户上下文拦截器。
 *
 * <p>覆盖全部业务路径 {@code /b/**}（含 /b/auth、/b/admin、/b/doctor）。拦截器为被动式：
 * 仅当请求携带 X-User-Id 时加载上下文，不干扰 B 端 Web 的 Sa-Token Bearer 鉴权。
 *
 * @author lezoo17
 * @since 2026-08-10
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    /** B 端网关路径前缀：与 C 端（{@code /c/**}）、AI（{@code /api/ai/**}）按前缀隔离。 */
    private static final String B_PATH_PATTERN = "/b/**";

    private final UserContextInterceptor userContextInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(userContextInterceptor).addPathPatterns(B_PATH_PATTERN);
    }
}
