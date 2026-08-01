package com.sphp.patient.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * C端用户账号状态枚举。
 */
@Getter
@RequiredArgsConstructor
public enum CUserStatusEnum {

    /** 启用状态，允许登录和使用业务功能 */
    ENABLED("ENABLED"),
    /** 停用状态，禁止登录 */
    DISABLED("DISABLED");

    /** 数据库存储值 */
    private final String value;
}
