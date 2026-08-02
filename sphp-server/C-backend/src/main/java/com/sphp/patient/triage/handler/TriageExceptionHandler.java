package com.sphp.patient.triage.handler;

import com.sphp.patient.triage.controller.TriageController;
import com.sphp.shared.common.enums.ErrorCodeEnum;
import com.sphp.shared.exception.BusinessException;
import com.sphp.shared.result.Result;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 导诊接口专用异常处理器。
 */
@RestControllerAdvice(assignableTypes = TriageController.class)
public class TriageExceptionHandler {

    /**
     * 将业务异常转换为约定 HTTP 状态和统一响应。
     *
     * @param exception 业务异常
     * @return 统一错误响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> triageHandleBusinessException(BusinessException exception) {
        return ResponseEntity.status(triageResolveStatus(exception.getCode()))
                .body(Result.error(exception.getCode(), exception.getMessage()));
    }

    /**
     * 区分症状内容非法与其他请求参数校验失败。
     *
     * @param exception 请求体校验异常
     * @return HTTP 400 统一错误响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> triageHandleValidationException(MethodArgumentNotValidException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldError();
        String message = fieldError == null ? "请求参数校验失败" : fieldError.getDefaultMessage();
        ErrorCodeEnum errorCode = fieldError != null && "symptom".equals(fieldError.getField())
                ? ErrorCodeEnum.ILLEGAL_INPUT_CONTENT
                : ErrorCodeEnum.INVALID_PARAMETER;
        return ResponseEntity.badRequest().body(Result.error(errorCode, message));
    }

    /**
     * 处理路径、请求头和 JSON 解析错误。
     *
     * @param exception 参数解析异常
     * @return HTTP 400 统一错误响应
     */
    @ExceptionHandler({ConstraintViolationException.class, MissingRequestHeaderException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<Result<Void>> triageHandleBadRequestException(Exception exception) {
        return ResponseEntity.badRequest().body(Result.error(ErrorCodeEnum.INVALID_PARAMETER, "请求参数错误"));
    }

    /**
     * 处理未预期异常，避免泄漏内部实现。
     *
     * @param exception 未处理异常
     * @return HTTP 500 统一错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> triageHandleSystemException(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(ErrorCodeEnum.SYSTEM_ERROR, "系统执行出错"));
    }

    /**
     * 根据业务码解析 HTTP 响应状态。
     *
     * @param errorCode 业务码
     * @return HTTP 状态
     */
    private HttpStatus triageResolveStatus(String errorCode) {
        return switch (errorCode) {
            case "A0301" -> HttpStatus.FORBIDDEN;
            case "A0402" -> HttpStatus.NOT_FOUND;
            case "A0506" -> HttpStatus.CONFLICT;
            case "B0001" -> HttpStatus.INTERNAL_SERVER_ERROR;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
}
