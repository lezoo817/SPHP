package com.sphp.patient.common.enums;

/**
 * 问诊文字消息发送方类型枚举。
 */
public enum ConsultationMessageSenderTypeEnum {

    /** 患者或其代管账号发送的消息 */
    PATIENT,
    /** 医生发送的问诊消息 */
    DOCTOR,
    /** 系统自动生成的提示消息 */
    SYSTEM
}
