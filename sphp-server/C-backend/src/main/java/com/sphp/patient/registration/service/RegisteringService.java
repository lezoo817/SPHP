package com.sphp.patient.registration.service;

import com.sphp.patient.auth.exception.CAuthException;
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
     * @throws CAuthException 就诊人、医院链路、号源或状态不满足要求时抛出
     */
    RegisteringAppointmentCreateVO registeringCreateAppointment(RegisteringAppointmentCreateRequest request);

    /**
     *  查询当前账号可访问的挂号订单列表。
     * @param patientId 就诊人 ID
     * @param status 订单状态
     * @param pageNo 页码
     * @param pageSize 每页数量
     * @return 挂号订单列表
     */
    RegisteringAppointmentListVO registeringListAppointments(Long patientId, String status, Integer pageNo, Integer pageSize);

    /**
     * 查询挂号订单详情。
     * @param appointmentId 挂号订单 ID
     * @return 挂号订单详情
     */
    RegisteringAppointmentDetailVO registeringGetAppointment(Long appointmentId);


    /**
     * 取消挂号订单。
     * @param appointmentId 挂号订单 ID
     * @return 取消挂号订单结果
     */
    RegisteringAppointmentCancelVO registeringCancelAppointment(Long appointmentId);


    /**
     * 创建候补挂号订单。
     * @param request 候补挂号请求参数
     * @return 候补挂号订单信息
     */
    RegisteringWaitlistCreateVO registeringCreateWaitlist(RegisteringWaitlistCreateRequest request);


    /**
     * 模拟支付挂号订单。
     * @param paymentId 支付单 ID
     * @param request 模拟支付请求参数
     * @return 模拟支付结果
     */
    RegisteringPaymentSuccessVO registeringSimulatePayment(Long paymentId, RegisteringPaymentSimulateRequest request);

    /**
     * 查询挂号支付单状态。
     * @param paymentId 支付单 ID
     * @return 支付单状态
     */
    RegisteringPaymentStatusVO registeringGetPayment(Long paymentId);
}
