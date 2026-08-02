package com.sphp.patient.common.enums;

/**
 * 用药计划允许的状态变更动作。
 */
public enum ProposalMedicationActionEnum {
    /** 暂停正在执行的用药计划。 */
    PAUSE,
    /** 恢复已经暂停的用药计划。 */
    RESUME,
    /** 完成正在执行或暂停中的用药计划。 */
    COMPLETE
}
