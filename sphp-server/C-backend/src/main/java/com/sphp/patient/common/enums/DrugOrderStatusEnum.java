package com.sphp.patient.common.enums;

/**
 * 购药订单状态枚举。
 */
public enum DrugOrderStatusEnum {

    /** 未提交支付的草稿状态 */
    DRAFT,

    /** 等待付款的购药订单 */
    PENDING_PAYMENT,

    /** 已付款购药订单 */
    PAID,

    /** 用户主动取消的订单 */
    CANCELLED,

    /** 支付窗口超时关闭的订单 */
    EXPIRED
}
