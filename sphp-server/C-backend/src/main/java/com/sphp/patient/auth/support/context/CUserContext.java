package com.sphp.patient.auth.support.context;

import com.sphp.patient.auth.exception.CAuthException;
import com.sphp.shared.common.enums.ErrorCodeEnum;
import org.springframework.http.HttpStatus;

/**
 * C端请求线程用户上下文。
 */
public final class CUserContext {

    /** 当前线程用户身份 */
    private static final ThreadLocal<CUserPrincipal> CONTEXT = new ThreadLocal<>();

    /**
     * 写入当前用户身份。
     *
     * @param principal 已验证的用户身份
     */
    public static void set(CUserPrincipal principal) {
        CONTEXT.set(principal);
    }

    /**
     * 获取当前用户身份。
     *
     * @return 当前身份，不存在时返回 null
     */
    public static CUserPrincipal get() {
        return CONTEXT.get();
    }

    /**
     * 获取当前必需的用户身份。
     *
     * @return 当前身份
     * @throws CAuthException 当前请求未建立身份时抛出
     */
    public static CUserPrincipal getRequired() {
        CUserPrincipal principal = CONTEXT.get();
        if (principal == null) {
            throw new CAuthException(ErrorCodeEnum.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "当前登录状态无效");
        }
        return principal;
    }

    /**
     * 清理当前线程用户身份，防止线程池复用导致身份串扰。
     */
    public static void clear() {
        CONTEXT.remove();
    }

    /**
     * 防止上下文工具类被实例化。
     */
    private CUserContext() {
    }
}
