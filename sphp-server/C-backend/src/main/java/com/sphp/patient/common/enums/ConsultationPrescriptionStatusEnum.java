package com.sphp.patient.common.enums;

/**
 * C端查询处方使用的处方状态枚举。
 */
public enum ConsultationPrescriptionStatusEnum {

    /** 医生正在编辑的处方 */
    DRAFT,

    /** 等待审核的处方 */
    SUBMITTED,

    /** 已审核通过且可向患者展示的处方 */
    APPROVED,

    /** 审核未通过的处方 */
    REJECTED,

    /** 已取消的处方 */
    CANCELLED
}
