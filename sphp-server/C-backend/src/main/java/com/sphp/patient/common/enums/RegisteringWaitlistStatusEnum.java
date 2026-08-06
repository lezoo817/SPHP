package com.sphp.patient.common.enums;

/**
 * 挂号候补状态枚举。
 */
public enum RegisteringWaitlistStatusEnum {
    // 排队中，尚未获得可预约提醒。
    WAITING,

    // 已通知，候补人可在有效期内通过正常挂号接口预约。
    NOTIFIED,

    // 已履约，候补人已成功锁定对应时段的挂号订单。
    FULFILLED,

    // 已取消，供后续候补取消能力使用。
    CANCELLED,

    // 已过期，通知超时或时段已开始。
    EXPIRED
}
