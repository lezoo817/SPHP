package com.sphp.patient.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 就诊人性别枚举。
 */
@Getter
@RequiredArgsConstructor
public enum GenderEnum {

    /** 男性 */
    MALE("MALE"),

    /** 女性 */
    FEMALE("FEMALE"),

    /** 未知或不便提供 */
    UNKNOWN("UNKNOWN");

    /** 数据库存储值 */
    private final String value;
}
