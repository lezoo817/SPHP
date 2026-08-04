package com.sphp.patient.order.handler;
import com.sphp.patient.order.controller.OrderController;
import com.sphp.patient.order.controller.PaymentController;
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
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static com.sphp.shared.common.constant.CommonConstant.DEFAULT_SYSTEM_ERROR_MESSAGE;
import static com.sphp.shared.common.enums.ErrorCodeEnum.INVALID_PARAMETER;
import static com.sphp.shared.common.enums.ErrorCodeEnum.SYSTEM_ERROR;

/** C端购药与统一支付异常处理器。 */
@Slf4j @RestControllerAdvice(assignableTypes={OrderController.class, PaymentController.class})
public class OrderExceptionHandler {
    /** 转换购药业务异常。 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException e) {
        return ResponseEntity
                .status(status(e.getCode()))
                .body(Result.error(e.getCode(),e.getMessage()));
    }

    /** 处理请求字段校验失败。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValid(MethodArgumentNotValidException e) {
        FieldError f=e.getBindingResult().getFieldError();
        return ResponseEntity.badRequest()
                .body(Result.error(INVALID_PARAMETER,f==null?"请求参数校验失败":f.getDefaultMessage()));
    }

    /** 处理路径与查询参数校验失败。 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraint(ConstraintViolationException e) {
        return ResponseEntity.badRequest()
                .body(Result.error(INVALID_PARAMETER,"请求参数校验失败"));
    }

    /** 处理缺少幂等键。 */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Result<Void>> handleHeader(MissingRequestHeaderException e) {
        return ResponseEntity.badRequest()
                .body(Result.error(INVALID_PARAMETER,"请求头"+e.getHeaderName()+"不能为空")); }

    /** 处理未预期系统异常。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception e) {
        log.error("C端购药系统异常",e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(SYSTEM_ERROR,DEFAULT_SYSTEM_ERROR_MESSAGE)); }

    /** 解析业务码对应 HTTP 状态。 */
    private HttpStatus status(String code) {
        return switch(code) {
            case "A0301" -> HttpStatus.FORBIDDEN;
            case "A0402" -> HttpStatus.NOT_FOUND;
            case "A0430","A0441","A0443","A0506","B0201","B0202","B0300" -> HttpStatus.CONFLICT;
            case "B0001" -> HttpStatus.INTERNAL_SERVER_ERROR;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
}
