package com.sphp.patient.family.handler;

import com.sphp.patient.auth.exception.CAuthException;
import com.sphp.patient.family.controller.ProfileController;
import com.sphp.shared.common.constant.CommonConstant;
import com.sphp.shared.common.enums.ErrorCodeEnum;
import com.sphp.shared.result.Result;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static com.sphp.shared.common.constant.CommonConstant.DEFAULT_SYSTEM_ERROR_MESSAGE;
import static com.sphp.shared.common.enums.ErrorCodeEnum.INVALID_PARAMETER;
import static com.sphp.shared.common.enums.ErrorCodeEnum.SYSTEM_ERROR;

/**
 * C端个人资料控制器异常处理器。
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = ProfileController.class)
public class ProfileExceptionHandler {

    /**
     * 将资料业务异常转换为约定 HTTP 状态和统一响应。
     *
     * @param exception C端业务异常
     * @return 统一错误响应
     */
    @ExceptionHandler(CAuthException.class)
    public ResponseEntity<Result<Void>> handleAuthException(CAuthException exception) {
        log.warn("C端个人资料异常 code={}, msg={}", exception.getCode(), exception.getMessage());
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
        return ResponseEntity.badRequest().body(Result.error(INVALID_PARAMETER, message));
    }

    /**
     * 处理请求体缺失或 JSON 格式错误。
     *
     * @param exception 请求体解析异常
     * @return HTTP 400 统一错误响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleNotReadableException(HttpMessageNotReadableException exception) {
        log.warn("C端个人资料请求体解析失败: {}", exception.getMessage());
        return ResponseEntity.badRequest().body(Result.error(INVALID_PARAMETER, "请求体缺失或格式错误"));
    }

    /**
     * 处理状态变更接口缺少必填请求头的异常。
     *
     * @param exception 缺失请求头异常
     * @return HTTP 400 统一错误响应
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Result<Void>> handleMissingRequestHeaderException(MissingRequestHeaderException exception) {
        return ResponseEntity.badRequest().body(Result.error(INVALID_PARAMETER,
                "请求头" + exception.getHeaderName() + "不能为空"));
    }

    /**
     * 处理请求参数约束校验异常。
     *
     * @param exception 参数约束校验异常
     * @return HTTP 400 统一错误响应
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolationException(ConstraintViolationException exception) {
        String message = exception.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .findFirst()
                .orElse("参数校验失败");
        return ResponseEntity.badRequest().body(Result.error(INVALID_PARAMETER, message));
    }

    /**
     * 处理未预期的个人资料接口异常。
     *
     * @param exception 未预期异常
     * @return HTTP 500 统一错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception exception) {
        log.error("C端个人资料系统异常", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(SYSTEM_ERROR, DEFAULT_SYSTEM_ERROR_MESSAGE));
    }
}
