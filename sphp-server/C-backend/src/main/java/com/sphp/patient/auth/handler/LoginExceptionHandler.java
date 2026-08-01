package com.sphp.patient.auth.handler;

import com.sphp.patient.auth.controller.LoginController;
import com.sphp.patient.auth.exception.CAuthException;
import com.sphp.shared.common.constant.CommonConstant;
import com.sphp.shared.common.enums.ErrorCodeEnum;
import com.sphp.shared.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * C端登录控制器异常处理器。
 */
@Slf4j
@RestControllerAdvice(assignableTypes = LoginController.class)
public class LoginExceptionHandler {

    /**
     * 处理认证业务异常并保留业务约定的 HTTP 状态。
     *
     * @param exception 认证业务异常
     * @return 统一错误响应
     */
    @ExceptionHandler(CAuthException.class)
    public ResponseEntity<Result<Void>> handleAuthException(CAuthException exception) {
        log.warn("C端认证异常 code={}, msg={}", exception.getCode(), exception.getMessage());
        return ResponseEntity.status(exception.getHttpStatus())
                .body(Result.error(exception.getCode(), exception.getMessage()));
    }

    /**
     * 处理请求 DTO 参数校验异常。
     *
     * @param exception 参数校验异常
     * @return HTTP 400 统一错误响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidException(MethodArgumentNotValidException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldError();
        String message = fieldError == null ? "参数校验失败" : fieldError.getDefaultMessage();
        return ResponseEntity.badRequest().body(Result.error(ErrorCodeEnum.INVALID_PARAMETER, message));
    }

    /**
     * 处理请求体缺失或 JSON 格式错误。
     *
     * @param exception 请求体解析异常
     * @return HTTP 400 统一错误响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleNotReadableException(HttpMessageNotReadableException exception) {
        log.warn("C端认证请求体解析失败: {}", exception.getMessage());
        return ResponseEntity.badRequest()
                .body(Result.error(ErrorCodeEnum.INVALID_PARAMETER, "请求体缺失或格式错误"));
    }

    /**
     * 处理未预期的认证接口异常。
     *
     * @param exception 未预期异常
     * @return HTTP 500 统一错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception exception) {
        log.error("C端认证系统异常", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(ErrorCodeEnum.SYSTEM_ERROR, CommonConstant.DEFAULT_SYSTEM_ERROR_MESSAGE));
    }
}
