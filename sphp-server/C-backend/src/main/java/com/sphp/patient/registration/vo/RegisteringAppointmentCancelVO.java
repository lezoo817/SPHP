package com.sphp.patient.registration.vo;
import lombok.Builder;
import lombok.Getter;
/** 挂号订单取消响应对象。 */
@Getter @Builder public class RegisteringAppointmentCancelVO { /** 挂号订单 ID */ private final Long appointmentId; /** 取消后状态 */ private final String status; }
