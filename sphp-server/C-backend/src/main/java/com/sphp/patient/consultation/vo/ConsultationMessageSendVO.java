package com.sphp.patient.consultation.vo;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * 患者发送文字消息结果。
 */
@Getter
@Builder
public class ConsultationMessageSendVO {

    /** 消息 ID */
    private final Long messageId;
    /** 问诊记录 ID */
    private final Long consultationId;
    /** 发送方类型，固定为 PATIENT */
    private final String senderType;
    /** 已保存文字内容 */
    private final String content;
    /** 创建时间 */
    private final OffsetDateTime createdAt;
}
