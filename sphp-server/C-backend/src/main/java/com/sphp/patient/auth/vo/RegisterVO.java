package com.sphp.patient.auth.vo;

import lombok.Builder;
import lombok.Getter;

/**
 * C端账号注册响应对象。
 */
@Getter
@Builder
public class RegisterVO {

    /** 新建 C端用户 ID */
    private final Long userId;

    /** 登录账号 */
    private final String account;
}
