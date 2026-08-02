package com.sphp.patient.registration.service;

import com.sphp.patient.registration.dto.RegisteringAppointmentCreateRequest;
import com.sphp.patient.registration.vo.RegisteringAppointmentCreateVO;

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
}
