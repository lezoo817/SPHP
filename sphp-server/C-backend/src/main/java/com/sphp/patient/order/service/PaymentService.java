package com.sphp.patient.order.service;
import com.sphp.patient.registration.dto.RegisteringPaymentSimulateRequest;
import com.sphp.patient.registration.vo.RegisteringPaymentSuccessVO;

/**
 * C端统一支付服务。
 */
public interface PaymentService {

    /**
     * 按支付单的唯一业务关联分派挂号或购药支付。
     * @param paymentId 支付单 ID
     * @param request 支付请求
     * @return 支付成功结果
     */
    RegisteringPaymentSuccessVO simulatePayment(Long paymentId, RegisteringPaymentSimulateRequest request);
}
