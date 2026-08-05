package com.sphp.admin.common;

import com.sphp.admin.auth.entity.BUser;

/**
 * 当前登录用户线程上下文容器（系分 §8.2）。
 *
 * <p>Agent 调用经 {@link UserContextInterceptor} 解析 X-User-Id 后写入；
 * 业务层通过 {@link #getContext()} 获取完整用户上下文。请求结束后由拦截器
 * {@code afterCompletion} 调用 {@link #clear()} 清理，防止线程复用串扰与内存泄漏。
 */
public final class UserContextHolder {

    private static final ThreadLocal<UserContext> HOLDER = new ThreadLocal<>();

    private UserContextHolder() {
    }

    /** 设置当前请求用户上下文（Agent 通道）。 */
    public static void setContext(UserContext context) {
        HOLDER.set(context);
    }

    /** 获取当前请求用户上下文；Agent 通道未建立（B 端 Web 请求）时为 null。 */
    public static UserContext getContext() {
        return HOLDER.get();
    }

    /** 获取当前登录用户；未建立上下文时返回 null。 */
    public static BUser getCurrentUser() {
        UserContext ctx = HOLDER.get();
        return ctx == null ? null : ctx.user();
    }

    /** 请求结束后清理，防止线程复用导致的上下文串扰与内存泄漏。 */
    public static void clear() {
        HOLDER.remove();
    }
}
