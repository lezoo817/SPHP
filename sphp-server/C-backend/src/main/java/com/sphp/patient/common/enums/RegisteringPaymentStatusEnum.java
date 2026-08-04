package com.sphp.patient.common.enums;

/**
 * 挂号支付单状态枚举。
 */
public enum RegisteringPaymentStatusEnum {

    /** 待付款支付单 */
    PENDING,

    /** 已付款支付单 */
    SUCCESS,

    /** 付款失败支付单 */
    FAILED,

    /** 已关闭支付单 */
    CLOSED
}
