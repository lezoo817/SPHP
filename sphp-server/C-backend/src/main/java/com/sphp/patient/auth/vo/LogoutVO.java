package com.sphp.patient.auth.vo;

import lombok.Builder;
import lombok.Getter;

/**
 * C端退出登录响应。
 */
@Getter
@Builder
public class LogoutVO {

    /** 当前会话是否已退出 */
    private final boolean loggedOut;
}
