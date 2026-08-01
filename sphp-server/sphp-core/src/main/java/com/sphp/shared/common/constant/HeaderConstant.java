package com.sphp.shared.common.constant;

/**
 * HTTP 公共请求头常量。
 */
public final class HeaderConstant {

    /** Bearer 令牌请求头 */
    public static final String AUTHORIZATION = "Authorization";
    /** 全链路追踪号请求头 */
    public static final String TRACE_ID = "X-Trace-Id";
    /** 状态变更接口幂等键请求头 */
    public static final String IDEMPOTENCY_KEY = "X-Idempotency-Key";
    /** 受控内网调用用户身份请求头 */
    public static final String USER_ID = "X-User-Id";

    /**
     * 防止工具类被实例化。
     */
    private HeaderConstant() {
    }
}
