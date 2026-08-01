package com.sphp.shared.common.constant;

/**
 * 跨端通用业务常量。
 */
public final class CommonConstant {

    /** 成功业务码 */
    public static final String SUCCESS_CODE = "00000";
    /** 默认成功提示 */
    public static final String DEFAULT_SUCCESS_MESSAGE = "操作成功";
    /** 默认系统异常提示 */
    public static final String DEFAULT_SYSTEM_ERROR_MESSAGE = "系统内部错误，请稍后重试";

    /**
     * 防止工具类被实例化。
     */
    private CommonConstant() {
    }
}
