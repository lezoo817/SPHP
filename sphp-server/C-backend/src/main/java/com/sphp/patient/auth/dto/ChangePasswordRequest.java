package com.sphp.patient.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * C端修改登录密码请求。
 */
@Getter
@Setter
@NoArgsConstructor
public class ChangePasswordRequest {

    /** 当前登录密码 */
    @NotBlank(message = "当前密码不能为空")
    private String oldPassword;

    /** 新登录密码，长度为 8 至 64 位 */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 64, message = "新密码长度必须为8至64位")
    private String newPassword;
}
