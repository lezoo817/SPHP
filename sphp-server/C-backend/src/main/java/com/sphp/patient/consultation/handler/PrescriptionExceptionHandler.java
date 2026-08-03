package com.sphp.patient.consultation.handler;

import com.sphp.patient.consultation.controller.PrescriptionController;
import com.sphp.shared.common.enums.ErrorCodeEnum;
import com.sphp.shared.exception.BusinessException;
import com.sphp.shared.result.Result;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static com.sphp.shared.common.enums.ErrorCodeEnum.INVALID_PARAMETER;
import static com.sphp.shared.common.enums.ErrorCodeEnum.SYSTEM_ERROR;

/**
 * 处方查询与解读接口专用异常处理器。
 */
@RestControllerAdvice(assignableTypes = PrescriptionController.class)
public class PrescriptionExceptionHandler {

    /**
     * 将处方业务异常转换为约定 HTTP 状态和统一响应。
     *
     * @param exception 业务异常
     * @return 统一错误响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> prescriptionHandleBusinessException(BusinessException exception) {
        return ResponseEntity.status(prescriptionResolveStatus(exception.getCode()))
                .body(Result.error(exception.getCode(), exception.getMessage()));
    }

    /**
     * 处理路径与查询参数校验失败。
     *
     * @param exception 参数校验异常
     * @return HTTP 400 统一错误响应
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> prescriptionHandleValidationException(ConstraintViolationException exception) {
        return ResponseEntity.badRequest().body(Result.error(INVALID_PARAMETER, "请求参数错误"));
    }

    /**
     * 处理未预期异常，避免泄漏内部实现。
     *
     * @param exception 未处理异常
     * @return HTTP 500 统一错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> prescriptionHandleSystemException(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(SYSTEM_ERROR, "系统执行出错"));
    }

    /**
     * 根据业务码解析 HTTP 状态。
     *
     * @param errorCode 业务码
     * @return HTTP 状态
     */
    private HttpStatus prescriptionResolveStatus(String errorCode) {
        return switch (errorCode) {
            case "A0301" -> HttpStatus.FORBIDDEN;
            case "A0402" -> HttpStatus.NOT_FOUND;
            case "B0202" -> HttpStatus.CONFLICT;
            case "B0001" -> HttpStatus.INTERNAL_SERVER_ERROR;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
}
