package com.sphp.patient.auth.config;

import com.sphp.patient.auth.filter.CJwtInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * C端 Web MVC 鉴权配置。
 */
@Configuration
@RequiredArgsConstructor
public class CWebMvcConfig implements WebMvcConfigurer {

    private final CJwtInterceptor cJwtInterceptor;

    /**
     * 注册 C端 JWT 拦截器及认证白名单。
     *
     * @param registry MVC 拦截器注册器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(cJwtInterceptor)
                .addPathPatterns("/c/v1/**")
                .excludePathPatterns(
                        "/c/v1/auth/captcha",
                        "/c/v1/auth/register",
                        "/c/v1/auth/login",
                        "/c/v1/auth/token/refresh",
                        "/c/v1/payments/callback"
                );
    }
}
