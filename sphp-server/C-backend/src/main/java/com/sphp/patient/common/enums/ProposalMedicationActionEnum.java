package com.sphp.patient.common.enums;

/**
 * 用药计划允许的状态变更动作。
 */
public enum ProposalMedicationActionEnum {
    /** 按处方频次开启循环用药提醒。 */
    ENABLE_REMINDER,
    /** 关闭循环用药提醒但不暂停用药计划。 */
    DISABLE_REMINDER,
    /** 暂停正在执行的用药计划。 */
    PAUSE,
    /** 恢复已经暂停的用药计划。 */
    RESUME,
    /** 完成正在执行或暂停中的用药计划。 */
    COMPLETE
}
