package com.sphp.shared.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 跨域访问属性配置。
 *
 * 读取 sphp.cors 前缀下的前端来源白名单。
 */
@ConfigurationProperties(prefix = "sphp.cors")
public class CorsProperties {

    private List<String> allowedOrigins = new ArrayList<>();

    /**
     * 获取允许跨域访问的前端来源列表。
     *
     * @return 前端来源白名单
     */
    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    /**
     * 设置允许跨域访问的前端来源列表。
     *
     * @param allowedOrigins 前端来源白名单
     */
    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
}
