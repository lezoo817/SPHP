package com.sphp.patient.common.enums;

/**
 * 随访计划生命周期状态。
 */
public enum ProposalFollowUpStatusEnum {
    /** 待患者确认随访提醒。 */
    PENDING_CONFIRM,

    /** 已确认并可进入提醒调度。 */
    CONFIRMED,

    /** 已完成的随访计划。 */
    COMPLETED,

    /** 已取消的随访计划。 */
    CANCELLED
}
