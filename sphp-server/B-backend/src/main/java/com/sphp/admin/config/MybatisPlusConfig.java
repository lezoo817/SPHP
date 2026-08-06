package com.sphp.admin.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置：注册分页拦截器（PostgreSQL 方言）。
 *
 * <p>所有分页查询（{@code Page<T>}、{@code IPage<T>}）经本拦截器翻译为
 * {@code LIMIT ... OFFSET ...} SQL；DbType 必须与实际数据库一致，否则分页 SQL
 * 语法会因方言差异而报错（B 端 PostgreSQL，C 端 MySQL，二者不可混用）。
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 注册 MyBatis-Plus 拦截器链。
     *
     * <p>当前仅含分页拦截器，后续若需乐观锁、SQL 性能分析、多租户等能力，按需
     * {@code addInnerInterceptor} 追加即可。
     *
     * @return MyBatis-Plus 拦截器实例（含分页方言）
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        return interceptor;
    }
}
