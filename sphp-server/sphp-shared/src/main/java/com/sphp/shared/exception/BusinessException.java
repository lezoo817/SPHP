package com.sphp.shared.exception;

import lombok.Getter;

/**
 * 业务异常，用于在 Service 层抛出可预期的业务错误。
 * 被 GlobalExceptionHandler 捕获后返回友好提示。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final Integer code;

    public BusinessException(String message) {
        super(message);
        this.code = 500;
    }

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}
