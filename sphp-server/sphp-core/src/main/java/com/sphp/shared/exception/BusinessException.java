package com.sphp.shared.exception;

import com.sphp.shared.common.enums.ErrorCode;
import com.sphp.shared.common.enums.ErrorCodeEnum;
import lombok.Getter;

/**
 * 业务异常，用于在 Service 层抛出可预期的业务错误。
 * 被 GlobalExceptionHandler 捕获后返回友好提示。
 *
 * <p>code 采用设计文档错误码约定（如 A0301/A0400/B0001），默认 B0001。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final String code;

    /** 默认系统异常 B0001 */
    public BusinessException(String message) {
        super(message);
        this.code = ErrorCodeEnum.SYSTEM_ERROR.getCode();
    }

    /**
     * 使用字符串业务码创建兼容既有调用的业务异常。
     *
     * @param code 字符串业务码
     * @param message 面向调用方的提示信息
     */
    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 使用错误码默认提示创建业务异常。
     *
     * @param errorCode 业务错误码
     */
    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage());
    }

    /**
     * 使用错误码和自定义提示创建业务异常。
     *
     * @param errorCode 业务错误码
     * @param message 面向调用方的提示信息
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }
}
