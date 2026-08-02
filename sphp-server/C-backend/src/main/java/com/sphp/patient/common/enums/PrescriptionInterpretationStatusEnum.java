package com.sphp.patient.common.enums;

/**
 * 处方解读生成状态枚举。
 */
public enum PrescriptionInterpretationStatusEnum {
    /** 解读等待后续生产链路生成。 */
    PENDING,
    /** 解读已生成，可对患者展示。 */
    READY,
    /** 解读生成失败，当前不可展示。 */
    FAILED
}
