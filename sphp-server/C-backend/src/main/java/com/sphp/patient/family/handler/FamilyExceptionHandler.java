package com.sphp.patient.family.handler;

import com.sphp.patient.family.controller.FamilyController;
import com.sphp.shared.common.constant.CommonConstant;
import com.sphp.shared.common.enums.ErrorCodeEnum;
import com.sphp.shared.exception.BusinessException;
import com.sphp.shared.result.Result;
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

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

/**
 * C端家庭成员控制器异常处理器。
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = FamilyController.class)
public class FamilyExceptionHandler {

    /**
     * 将家庭成员业务异常转换为约定 HTTP 状态和统一响应。
     *
     * @param exception 业务异常
     * @return 统一错误响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException exception) {
        log.warn("C端家庭成员异常 code={}, msg={}", exception.getCode(), exception.getMessage());
        return ResponseEntity.status(resolveHttpStatus(exception.getCode()))
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
        log.warn("C端家庭成员请求体解析失败: {}", exception.getMessage());
        return ResponseEntity.badRequest()
                .body(Result.error(ErrorCodeEnum.INVALID_PARAMETER, "请求体缺失或格式错误"));
    }

    /**
     * 处理状态变更接口缺少必填请求头的异常。
     *
     * @param exception 缺失请求头异常
     * @return HTTP 400 统一错误响应
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Result<Void>> handleMissingRequestHeaderException(MissingRequestHeaderException exception) {
        log.warn("C端家庭成员请求头缺失: {}", exception.getHeaderName());
        return ResponseEntity.badRequest()
                .body(Result.error(ErrorCodeEnum.INVALID_PARAMETER, "请求头" + exception.getHeaderName() + "不能为空"));
    }

    /**
     * 处理路径参数或请求头参数的约束校验异常。
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
        return ResponseEntity.badRequest().body(Result.error(ErrorCodeEnum.INVALID_PARAMETER, message));
    }

    /**
     * 处理未预期的家庭成员接口异常。
     *
     * @param exception 未预期异常
     * @return HTTP 500 统一错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception exception) {
        log.error("C端家庭成员系统异常", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(ErrorCodeEnum.SYSTEM_ERROR, CommonConstant.DEFAULT_SYSTEM_ERROR_MESSAGE));
    }

    /**
     * 解析业务码对应的 HTTP 状态。
     *
     * @param errorCode 业务码
     * @return HTTP 状态
     */
    private HttpStatus resolveHttpStatus(String errorCode) {
        return switch (errorCode) {
            case "A0402" -> HttpStatus.NOT_FOUND;
            case "A0443", "A0506", "B0202" -> HttpStatus.CONFLICT;
            case "A0301", "A0230" -> HttpStatus.UNAUTHORIZED;
            case "A0203" -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
}
