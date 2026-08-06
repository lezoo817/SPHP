package com.sphp.patient.common.enums;

/**
 * 问诊记录状态枚举。
 */
public enum ConsultationStatusEnum {

    /** 患者尚未提交的预问诊草稿 */
    DRAFT,

    /** 已提交并等待医生接诊 */
    PENDING,

    /** 医生已开始且允许患者发送消息 */
    IN_PROGRESS,

    /** 医生已完成问诊 */
    COMPLETED,

    /** 患者未按约完成问诊 */
    NO_SHOW
}
