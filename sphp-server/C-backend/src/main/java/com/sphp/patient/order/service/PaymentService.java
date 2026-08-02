package com.sphp.patient.order.service;
import com.sphp.patient.registration.dto.RegisteringPaymentSimulateRequest;
import com.sphp.patient.registration.vo.RegisteringPaymentSuccessVO;
/** C端统一支付服务。 */
public interface PaymentService { /** 按支付单关联业务模拟完成支付。 */ RegisteringPaymentSuccessVO simulatePayment(Long paymentId, RegisteringPaymentSimulateRequest request); }
