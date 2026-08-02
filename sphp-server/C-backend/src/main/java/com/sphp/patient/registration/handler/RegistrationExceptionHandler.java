package com.sphp.patient.registration.handler;

import com.sphp.patient.registration.controller.RegistrationController;
import com.sphp.shared.common.constant.CommonConstant;
import com.sphp.shared.common.enums.ErrorCodeEnum;
import com.sphp.shared.exception.BusinessException;
import com.sphp.shared.result.Result;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * C端挂号资源查询控制器异常处理器。
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = RegistrationController.class)
public class RegistrationExceptionHandler {

    /**
     * 将挂号资源业务异常转换为约定 HTTP 状态和统一响应。
     *
     * @param exception 业务异常
     * @return 统一错误响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException exception) {
        log.warn("C端挂号资源异常 code={}, msg={}", exception.getCode(), exception.getMessage());
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
     * 处理路径参数或查询参数约束校验异常。
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
     * 处理缺少必填查询参数的异常。
     *
     * @param exception 缺少查询参数异常
     * @return HTTP 400 统一错误响应
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParameterException(MissingServletRequestParameterException exception) {
        return ResponseEntity.badRequest().body(Result.error(ErrorCodeEnum.INVALID_PARAMETER,
                "请求参数" + exception.getParameterName() + "不能为空"));
    }

    /**
     * 处理查询参数类型无法转换的异常，例如日期格式错误。
     *
     * @param exception 参数类型转换异常
     * @return HTTP 400 统一错误响应
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatchException(MethodArgumentTypeMismatchException exception) {
        return ResponseEntity.badRequest().body(Result.error(ErrorCodeEnum.INVALID_PARAMETER,
                "请求参数" + exception.getName() + "格式错误"));
    }

    /**
     * 处理未预期的挂号资源查询异常。
     *
     * @param exception 未预期异常
     * @return HTTP 500 统一错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception exception) {
        log.error("C端挂号资源系统异常", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(ErrorCodeEnum.SYSTEM_ERROR, CommonConstant.DEFAULT_SYSTEM_ERROR_MESSAGE));
    }

    /**
     * 解析挂号资源业务码对应的 HTTP 状态。
     *
     * @param errorCode 业务码
     * @return HTTP 状态
     */
    private HttpStatus resolveHttpStatus(String errorCode) {
        return switch (errorCode) {
            case "A0402" -> HttpStatus.NOT_FOUND;
            case "A0301" -> HttpStatus.FORBIDDEN;
            case "A0420", "A0400" -> HttpStatus.BAD_REQUEST;
            case "B0001" -> HttpStatus.INTERNAL_SERVER_ERROR;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
}
