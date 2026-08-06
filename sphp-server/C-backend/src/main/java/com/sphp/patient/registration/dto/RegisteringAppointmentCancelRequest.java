package com.sphp.patient.registration.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 挂号订单取消请求参数。
 * 已支付挂号取消时必须提交当前登录密码，未支付取消保持无请求体兼容。
 */
@Getter
@Setter
public class RegisteringAppointmentCancelRequest {

    /** 当前 C 端登录密码，仅用于已支付挂号取消校验。 */
    @Size(max = 64, message = "loginPassword 长度不能超过64位")
    private String loginPassword;
}
