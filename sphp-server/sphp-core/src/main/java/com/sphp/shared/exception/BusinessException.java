package com.sphp.shared.exception;

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
        this.code = "B0001";
    }

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }
}
