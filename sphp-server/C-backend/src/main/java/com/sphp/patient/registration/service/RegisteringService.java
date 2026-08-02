package com.sphp.patient.registration.service;

import com.sphp.patient.registration.dto.RegisteringAppointmentCreateRequest;
import com.sphp.patient.registration.vo.RegisteringAppointmentCreateVO;
import com.sphp.patient.registration.vo.RegisteringAppointmentDetailVO;
import com.sphp.patient.registration.vo.RegisteringAppointmentListVO;
import com.sphp.patient.registration.vo.RegisteringAppointmentCancelVO;
import com.sphp.patient.registration.vo.RegisteringPaymentStatusVO;
import com.sphp.patient.registration.vo.RegisteringPaymentSuccessVO;
import com.sphp.patient.registration.vo.RegisteringWaitlistCreateVO;
import com.sphp.patient.registration.dto.RegisteringPaymentSimulateRequest;
import com.sphp.patient.registration.dto.RegisteringWaitlistCreateRequest;

/**
 * C端挂号订单与支付服务。
 */
public interface RegisteringService {

    /**
     * 创建挂号锁定订单并生成待支付单。
     *
     * @param request 创建挂号请求参数
     * @return 锁号成功后的订单与支付单信息
     * @throws com.sphp.patient.auth.exception.CAuthException 就诊人、医院链路、号源或状态不满足要求时抛出
     */
    RegisteringAppointmentCreateVO registeringCreateAppointment(RegisteringAppointmentCreateRequest request);

    /** 查询当前账号指定就诊人的挂号订单分页列表。 */
    RegisteringAppointmentListVO registeringListAppointments(Long patientId, String status, Integer pageNo, Integer pageSize);
    /** 查询当前账号可访问的挂号订单详情。 */
    RegisteringAppointmentDetailVO registeringGetAppointment(Long appointmentId);
    /** 取消当前账号可访问的未支付挂号订单。 */
    RegisteringAppointmentCancelVO registeringCancelAppointment(Long appointmentId);
    /** 创建当前账号就诊人的挂号候补登记。 */
    RegisteringWaitlistCreateVO registeringCreateWaitlist(RegisteringWaitlistCreateRequest request);
    /** 模拟支付当前账号的挂号支付单。 */
    RegisteringPaymentSuccessVO registeringSimulatePayment(Long paymentId, RegisteringPaymentSimulateRequest request);
    /** 查询当前账号的挂号支付单状态。 */
    RegisteringPaymentStatusVO registeringGetPayment(Long paymentId);
}
