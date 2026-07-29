package com.sphp.shared.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 统一后端的跨域访问配置。
 *
 * 允许在配置文件中声明的 B 端和 C 端前端来源访问应用接口。
 */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class CorsConfig implements WebMvcConfigurer {

    private final CorsProperties corsProperties;

    /**
     * 创建跨域访问配置。
     *
     * @param corsProperties 跨域访问属性配置
     */
    public CorsConfig(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    /**
     * 注册统一后端接口的跨域规则。
     *
     * @param registry 跨域映射注册器
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // context-path 已配置为 /api，此处匹配进入应用后的全部接口路径。
        registry.addMapping("/**")
                .allowedOrigins(corsProperties.getAllowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
