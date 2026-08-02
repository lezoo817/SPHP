package com.sphp.patient.health.handler;

import com.sphp.patient.health.controller.ProposalController;
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
 * 健康报告、用药与随访接口的专用异常处理器。
 */
@RestControllerAdvice(assignableTypes = ProposalController.class)
public class ProposalExceptionHandler {

    /**
     * 将业务异常映射为统一响应与约定 HTTP 状态。
     *
     * @param exception 业务异常
     * @return 统一错误响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> proposalHandleBusinessException(BusinessException exception) {
        return ResponseEntity.status(proposalResolveHttpStatus(exception.getCode()))
                .body(Result.error(exception.getCode(), exception.getMessage()));
    }

    /**
     * 处理请求体字段校验错误。
     *
     * @param exception 参数校验异常
     * @return HTTP 400 统一错误响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> proposalHandleValidationException(MethodArgumentNotValidException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldError();
        String message = fieldError == null ? "参数校验失败" : fieldError.getDefaultMessage();
        return ResponseEntity.badRequest().body(Result.error(ErrorCodeEnum.INVALID_PARAMETER, message));
    }

    /**
     * 处理路径、请求头和 JSON 格式错误。
     *
     * @param exception 参数解析异常
     * @return HTTP 400 统一错误响应
     */
    @ExceptionHandler({ConstraintViolationException.class, MissingRequestHeaderException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<Result<Void>> proposalHandleBadRequestException(Exception exception) {
        return ResponseEntity.badRequest().body(Result.error(ErrorCodeEnum.INVALID_PARAMETER, "请求参数错误"));
    }

    /**
     * 处理未预期的系统异常，避免向客户端暴露内部细节。
     *
     * @param exception 未处理异常
     * @return HTTP 500 统一错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> proposalHandleSystemException(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(ErrorCodeEnum.SYSTEM_ERROR, "系统执行出错"));
    }

    /**
     * 根据业务码还原接口契约规定的 HTTP 状态。
     *
     * @param code 业务码
     * @return HTTP 响应状态
     */
    private HttpStatus proposalResolveHttpStatus(String code) {
        return switch (code) {
            case "A0301" -> HttpStatus.FORBIDDEN;
            case "A0402" -> HttpStatus.NOT_FOUND;
            case "A0443", "A0506", "B0202" -> HttpStatus.CONFLICT;
            case "B0001" -> HttpStatus.INTERNAL_SERVER_ERROR;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
}
