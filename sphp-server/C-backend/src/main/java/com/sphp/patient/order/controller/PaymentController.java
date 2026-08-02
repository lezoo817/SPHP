package com.sphp.patient.order.controller;
import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.order.service.PaymentService;
import com.sphp.patient.registration.dto.RegisteringPaymentSimulateRequest;
import com.sphp.patient.registration.vo.RegisteringPaymentSuccessVO;
import com.sphp.patient.support.idempotency.CIdempotencyService;
import com.sphp.patient.support.idempotency.IdempotencyPayload;
import com.sphp.shared.common.constant.HeaderConstant;
import com.sphp.shared.result.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
/** C端统一模拟支付接口。 */
@RestController @Validated @RequestMapping("/c/v1") @RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService; private final CIdempotencyService idempotencyService;
    /** 模拟完成挂号或购药支付单。 */
    @PostMapping("/payments/{paymentId}/simulate-pay")
    public Result<RegisteringPaymentSuccessVO> simulatePayment(@PathVariable @Positive Long paymentId, @RequestHeader(HeaderConstant.IDEMPOTENCY_KEY) @NotBlank String idempotencyKey, @Valid @RequestBody RegisteringPaymentSimulateRequest request) {
        Long userId=CUserContext.getRequired().userId(); IdempotencyPayload<RegisteringPaymentSuccessVO> payload=idempotencyService.execute(userId,"/c/v1/payments/"+paymentId+"/simulate-pay",idempotencyKey,request,RegisteringPaymentSuccessVO.class,()->new IdempotencyPayload<>("支付成功",paymentService.simulatePayment(paymentId,request))); return Result.success(payload.message(),payload.data());
    }
}
