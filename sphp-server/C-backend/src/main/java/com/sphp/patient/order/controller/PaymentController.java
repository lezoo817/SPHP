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

import static com.sphp.shared.common.constant.HeaderConstant.IDEMPOTENCY_KEY;

/**
 * C端支付接口。
 */
@RestController
@Validated // 开启参数验证
@RequestMapping("/c/v1")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    // 幂等性服务
    private final CIdempotencyService idempotencyService;

    /**
     * 模拟支付。
     * @param paymentId 支付单 ID
     * @param idempotencyKey 请求幂等性标识
     * @param request 模拟支付请求
     * @return 模拟支付结果
     */
    @PostMapping("/payments/{paymentId}/simulate-pay")
    public Result<RegisteringPaymentSuccessVO> simulatePayment(@PathVariable @Positive Long paymentId,
                                                               @RequestHeader(IDEMPOTENCY_KEY) @NotBlank String idempotencyKey,
                                                               @Valid @RequestBody RegisteringPaymentSimulateRequest request) {
        Long userId=CUserContext.getRequired().userId();
        IdempotencyPayload<RegisteringPaymentSuccessVO> payload=idempotencyService
                .execute(userId,
                        "/c/v1/payments/"+paymentId+"/simulate-pay",
                        idempotencyKey,
                        request,RegisteringPaymentSuccessVO.class,
                        ()->new IdempotencyPayload<>("支付成功",
                                paymentService.simulatePayment(paymentId,request)));
        return Result.success(payload.message(),payload.data());
    }
}
