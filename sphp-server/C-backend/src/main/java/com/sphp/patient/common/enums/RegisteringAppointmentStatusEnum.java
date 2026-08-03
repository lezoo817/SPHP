package com.sphp.patient.common.enums;

/**
 * 挂号订单状态枚举。
 */
public enum RegisteringAppointmentStatusEnum {

    /** 待支付挂号订单 */
    UNPAID,

    /** 已支付挂号订单 */
    PAID,

    /** 已完成挂号订单 */
    COMPLETED,

    /** 已取消挂号订单 */
    CANCELLED
}
