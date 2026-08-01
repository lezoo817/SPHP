package com.sphp.patient.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * C端账号注册请求。
 */
@Getter
@Setter
@NoArgsConstructor
public class RegisterRequest {

    /** 登录账号，去除首尾空白后长度为 4 至 32 位 */
    @NotBlank(message = "登录账号不能为空")
    @Size(min = 4, max = 32, message = "登录账号长度必须为4至32位")
    private String account;
    /** 登录密码，长度为 8 至 64 位 */
    @NotBlank(message = "登录密码不能为空")
    @Size(min = 8, max = 64, message = "登录密码长度必须为8至64位")
    private String password;
    /** 图形验证码挑战标识 */
    @NotBlank(message = "验证码挑战标识不能为空")
    private String challengeId;
    /** 用户输入的图形验证码 */
    @NotBlank(message = "图形验证码不能为空")
    private String captchaCode;
}
