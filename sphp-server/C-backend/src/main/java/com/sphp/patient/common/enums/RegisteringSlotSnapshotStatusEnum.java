package com.sphp.patient.common.enums;

/**
 * 挂号号源快照状态枚举。
 */
public enum RegisteringSlotSnapshotStatusEnum {

    /** 可锁定号源 */
    AVAILABLE,

    /** 已锁定待支付号源 */
    LOCKED,

    /** 已售出号源 */
    SOLD,

    /** 超时号源 */
    EXPIRED,

    /** 已释放号源 */
    RELEASED
}
