package com.sphp.patient.order.service.impl;
import com.sphp.patient.auth.exception.CAuthException;
import com.sphp.patient.order.mapper.OrderDataMapper;
import com.sphp.patient.order.mapper.PaymentBusinessRecord;
import com.sphp.patient.order.service.OrderService;
import com.sphp.patient.order.service.PaymentService;
import com.sphp.patient.registration.dto.RegisteringPaymentSimulateRequest;
import com.sphp.patient.registration.service.RegisteringService;
import com.sphp.patient.registration.vo.RegisteringPaymentSuccessVO;
import com.sphp.shared.common.enums.ErrorCodeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
/** C端统一支付服务实现。 */
@Service @RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {
    private final OrderDataMapper orderDataMapper; private final RegisteringService registeringService; private final OrderService orderService;
    /** 按支付单的唯一业务关联分派挂号或购药支付。 */
    @Override public RegisteringPaymentSuccessVO simulatePayment(Long paymentId, RegisteringPaymentSimulateRequest request) {
        PaymentBusinessRecord payment=orderDataMapper.selectPaymentBusiness(paymentId);
        if(payment==null) throw new CAuthException(ErrorCodeEnum.INVALID_USER_INPUT,HttpStatus.NOT_FOUND,"支付单不存在");
        if(payment.appointmentId()!=null) return registeringService.registeringSimulatePayment(paymentId,request);
        if(payment.drugOrderId()!=null) return orderService.simulateDrugOrderPayment(paymentId,request);
        throw new CAuthException(ErrorCodeEnum.SYSTEM_ERROR,HttpStatus.INTERNAL_SERVER_ERROR,"支付单业务关联异常");
    }
}
