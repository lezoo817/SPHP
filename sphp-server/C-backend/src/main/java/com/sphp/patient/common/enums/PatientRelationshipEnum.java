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
    SELF("SELF"),
    /** 配偶 */
    SPOUSE("SPOUSE"),
    /** 父母 */
    PARENT("PARENT"),
    /** 子女 */
    CHILD("CHILD"),
    /** 其他关系 */
    OTHER("OTHER");

    /** 数据库存储值 */
    private final String value;
}
