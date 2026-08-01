package com.sphp.patient.auth.vo;

import lombok.Builder;
import lombok.Getter;

/**
 * C端修改登录密码响应。
 */
@Getter
@Builder
public class ChangePasswordVO {

    /** 登录密码是否修改成功 */
    private final boolean passwordChanged;
}
