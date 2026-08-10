package com.sphp.patient.notification.handler;

import com.sphp.patient.auth.exception.CAuthException;
import com.sphp.patient.notification.controller.NotificationController;
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
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static com.sphp.shared.common.constant.CommonConstant.DEFAULT_SYSTEM_ERROR_MESSAGE;
import static com.sphp.shared.common.enums.ErrorCodeEnum.INVALID_PARAMETER;
import static com.sphp.shared.common.enums.ErrorCodeEnum.SYSTEM_ERROR;

/**
 * C端站内通知控制器异常处理器。
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = NotificationController.class)
public class NotificationExceptionHandler {

    /**
     * 处理带 HTTP 状态的 C端业务异常。
     *
     * @param exception C端业务异常
     * @return 统一错误响应
     */
    @ExceptionHandler(CAuthException.class)
    public ResponseEntity<Result<Void>> handleAuthException(CAuthException exception) {
        log.warn("C端通知异常 code={}, msg={}", exception.getCode(), exception.getMessage());
        return ResponseEntity.status(exception.getHttpStatus()).body(Result.error(exception.getCode(), exception.getMessage()));
    }

    /**
     * 处理缺少幂等请求头的异常。
     *
     * @param exception 缺失请求头异常
     * @return HTTP 400 统一错误响应
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Result<Void>> handleMissingHeaderException(MissingRequestHeaderException exception) {
        return ResponseEntity.badRequest().body(Result.error(INVALID_PARAMETER,
                "请求头" + exception.getHeaderName() + "不能为空"));
    }

    /**
     * 处理路径和查询参数约束校验异常。
     *
     * @param exception 参数约束校验异常
     * @return HTTP 400 统一错误响应
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolationException(ConstraintViolationException exception) {
        String message = exception.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage) // 获取每个参数校验失败的提示信息
                .findFirst().orElse("参数校验失败");
        return ResponseEntity.badRequest().body(Result.error(INVALID_PARAMETER, message));
    }

    /**
     * 处理未预期的通知接口异常。
     *
     * @param exception 未预期异常
     * @return HTTP 500 统一错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception exception) {
        log.error("C端通知系统异常", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(SYSTEM_ERROR, DEFAULT_SYSTEM_ERROR_MESSAGE));
    }
}
