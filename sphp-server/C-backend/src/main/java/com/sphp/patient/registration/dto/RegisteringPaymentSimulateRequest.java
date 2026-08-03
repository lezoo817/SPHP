package com.sphp.patient.registration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 模拟支付请求参数。 */
@Getter @Setter
public class RegisteringPaymentSimulateRequest {
    /** 当前 C端登录密码，原样校验且不记录 */
    @NotBlank(message = "loginPassword 不能为空")
    @Size(max = 64, message = "loginPassword 长度不能超过64位")
    private String loginPassword;
}
