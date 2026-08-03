package com.sphp.patient.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * C端账号密码登录请求。
 */
@Getter
@Setter
@NoArgsConstructor
public class LoginRequest {

    /** C端登录账号 */
    @NotBlank(message = "登录账号不能为空")
    private String account;

    /** 登录密码 */
    @NotBlank(message = "登录密码不能为空")
    private String password;
}
