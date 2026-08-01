package com.sphp.patient.auth.vo;

import lombok.Builder;
import lombok.Getter;

/**
 * 登录响应中的 C端用户摘要。
 */
@Getter
@Builder
public class LoginUserVO {

    /** C端用户 ID */
    private final Long id;
    /** 登录账号 */
    private final String account;
}
