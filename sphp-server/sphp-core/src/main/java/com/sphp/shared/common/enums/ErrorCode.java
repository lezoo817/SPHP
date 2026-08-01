package com.sphp.shared.common.enums;

/**
 * 公共业务码契约。
 */
public interface ErrorCode {

    /**
     * 获取业务码。
     *
     * @return 字符串业务码
     */
    String getCode();

    /**
     * 获取面向调用方的默认中文提示。
     *
     * @return 默认中文提示
     */
    String getMessage();
}
