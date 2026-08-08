package com.sphp.patient.common.enums;

/**
 * C端站内通知展示类型枚举。
 */
public enum NotificationTypeEnum {

    /** 挂号相关通知 */
    APPOINTMENT,

    /** 购药订单相关通知 */
    DRUG_ORDER,

    /** 药品配送物流相关通知 */
    LOGISTICS,

    /** 用药计划提醒 */
    MEDICATION_REMINDER,

    /** 随访计划提醒 */
    FOLLOW_UP_REMINDER,

    /** 在线问诊医生回复通知 */
    CONSULTATION,

    /** 通用系统通知 */
    SYSTEM
}
