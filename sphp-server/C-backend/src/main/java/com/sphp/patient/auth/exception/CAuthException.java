package com.sphp.patient.auth.exception;

import com.sphp.shared.common.enums.ErrorCode;
import com.sphp.shared.exception.BusinessException;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * C端认证业务异常，携带接口契约要求的 HTTP 状态。
 */
@Getter
public class CAuthException extends BusinessException {

    /** HTTP 响应状态 */
    private final HttpStatus httpStatus;

    /**
     * 使用业务码默认提示创建认证异常。
     *
     * @param errorCode 业务错误码
     * @param httpStatus HTTP 响应状态
     */
    public CAuthException(ErrorCode errorCode, HttpStatus httpStatus) {
        this(errorCode, httpStatus, errorCode.getMessage());
    }

    /**
     * 使用业务码、自定义提示和 HTTP 状态创建认证异常。
     *
     * @param errorCode 业务错误码
     * @param httpStatus HTTP 响应状态
     * @param message 面向调用方的提示信息
     */
    public CAuthException(ErrorCode errorCode, HttpStatus httpStatus, String message) {
        super(errorCode, message);
        this.httpStatus = httpStatus;
    }
}
