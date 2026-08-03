package com.sphp.patient.order.handler;

import com.sphp.patient.order.controller.DeliveryController;
import com.sphp.shared.common.constant.CommonConstant;
import com.sphp.shared.common.enums.ErrorCodeEnum;
import com.sphp.shared.exception.BusinessException;
import com.sphp.shared.result.Result;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * C端收货地址与模拟配送接口异常处理器。
 */
@Slf4j
@RestControllerAdvice(assignableTypes = DeliveryController.class)
public class DeliveryExceptionHandler {

    /** 处理收货地址业务异常。 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> deliveryHandleBusiness(BusinessException exception) {
        return ResponseEntity.status(deliveryStatus(exception.getCode())).body(Result.error(exception.getCode(), exception.getMessage()));
    }

    /** 处理请求体参数校验失败。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> deliveryHandleValidation(MethodArgumentNotValidException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldError();
        return ResponseEntity.badRequest().body(Result.error(ErrorCodeEnum.INVALID_PARAMETER,
                fieldError == null ? "请求参数校验失败" : fieldError.getDefaultMessage()));
    }

    /** 处理路径和查询参数校验失败。 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> deliveryHandleConstraint(ConstraintViolationException exception) {
        return ResponseEntity.badRequest().body(Result.error(ErrorCodeEnum.INVALID_PARAMETER, "请求参数校验失败"));
    }

    /** 处理缺少幂等键请求头。 */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Result<Void>> deliveryHandleHeader(MissingRequestHeaderException exception) {
        return ResponseEntity.badRequest().body(Result.error(ErrorCodeEnum.INVALID_PARAMETER, "请求头" + exception.getHeaderName() + "不能为空"));
    }

    /** 处理缺少必填查询参数。 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> deliveryHandleRequestParameter(MissingServletRequestParameterException exception) {
        return ResponseEntity.badRequest().body(Result.error(ErrorCodeEnum.INVALID_PARAMETER, "请求参数" + exception.getParameterName() + "不能为空"));
    }

    /** 处理未预期系统异常。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> deliveryHandleUnknown(Exception exception) {
        log.error("C端收货地址系统异常", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(ErrorCodeEnum.SYSTEM_ERROR, CommonConstant.DEFAULT_SYSTEM_ERROR_MESSAGE));
    }

    /**
     * 将业务码映射为 HTTP 状态。
     *
     * @param code 业务码
     * @return HTTP 状态
     */
    private HttpStatus deliveryStatus(String code) {
        return switch (code) {
            case "A0301" -> HttpStatus.FORBIDDEN;
            case "A0402" -> HttpStatus.NOT_FOUND;
            case "A0506", "B0202" -> HttpStatus.CONFLICT;
            case "B0001" -> HttpStatus.INTERNAL_SERVER_ERROR;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
}
