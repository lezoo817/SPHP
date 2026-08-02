package com.sphp.patient.common.enums;

/**
 * 用药计划生命周期状态。
 */
public enum ProposalMedicationStatusEnum {
    /** 正在执行，可暂停或完成。 */
    ACTIVE,
    /** 已暂停，可恢复或完成。 */
    PAUSED,
    /** 已完成，不再允许状态变更。 */
    COMPLETED
}
