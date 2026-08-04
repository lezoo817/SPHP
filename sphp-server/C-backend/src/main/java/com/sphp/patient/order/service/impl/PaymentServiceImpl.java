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

import static com.sphp.shared.common.enums.ErrorCodeEnum.INVALID_USER_INPUT;
import static com.sphp.shared.common.enums.ErrorCodeEnum.SYSTEM_ERROR;

/** C端统一支付服务实现。 */
@Service @RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {
    // 数据访问接口
    private final OrderDataMapper orderDataMapper;
    // 挂号服务接口
    private final RegisteringService registeringService;
    // 购药服务接口
    private final OrderService orderService;

    /**
     * 模拟支付
     * @param paymentId 支付单ID
     * @param request 模拟支付请求
     * @return 模拟支付结果
     */
    @Override public RegisteringPaymentSuccessVO simulatePayment(Long paymentId, RegisteringPaymentSimulateRequest request) {

        PaymentBusinessRecord payment=orderDataMapper.selectPaymentBusiness(paymentId);
        // 支付单不存在
        if(payment==null)
            throw new CAuthException(INVALID_USER_INPUT,HttpStatus.NOT_FOUND,"支付单不存在");
        // 挂号支付
        if(payment.appointmentId()!=null)
            return registeringService.registeringSimulatePayment(paymentId,request);
        // 购药支付
        if(payment.drugOrderId()!=null)
            return orderService.simulateDrugOrderPayment(paymentId,request);

        throw new CAuthException(SYSTEM_ERROR,HttpStatus.INTERNAL_SERVER_ERROR,"支付单业务关联异常");
    }
}
