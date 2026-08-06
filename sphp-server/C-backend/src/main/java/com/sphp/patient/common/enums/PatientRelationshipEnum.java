package com.sphp.patient.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 就诊人与 C端账号关系枚举。
 */
@Getter
@RequiredArgsConstructor
public enum PatientRelationshipEnum {

    /** 账号本人 */
    SELF("SELF", "本人"),

    /** 配偶 */
    SPOUSE("SPOUSE", "配偶"),

    /** 父母 */
    PARENT("PARENT", "父母"),

    /** 子女 */
    CHILD("CHILD", "子女"),

    /** 其他关系 */
    OTHER("OTHER", "其他");


    /** 数据库存储值 */
    private final String value;

    /** 面向 C端展示的关系名称 */
    private final String displayName;
}
