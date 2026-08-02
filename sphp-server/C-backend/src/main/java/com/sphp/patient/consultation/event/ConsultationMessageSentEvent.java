package com.sphp.patient.consultation.event;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 患者文字问诊消息已持久化事件，不包含病情原文。
 *
 * @param eventId 事件唯一 ID
 * @param eventType 事件类型
 * @param consultationId 问诊记录 ID
 * @param doctorId 接诊医生 ID
 * @param patientId 就诊人 ID
 * @param userId C端用户 ID
 * @param occurredAt 事件发生时间
 */
public record ConsultationMessageSentEvent(String eventId, String eventType, Long consultationId,
                                           Long doctorId, Long patientId, Long userId,
                                           OffsetDateTime occurredAt) implements Serializable {

    /**
     * 创建患者文字消息发送事件。
     *
     * @param consultationId 问诊记录 ID
     * @param doctorId 接诊医生 ID
     * @param patientId 就诊人 ID
     * @param userId C端用户 ID
     * @return 新事件
     */
    public static ConsultationMessageSentEvent of(Long consultationId, Long doctorId, Long patientId, Long userId) {
        return new ConsultationMessageSentEvent(UUID.randomUUID().toString(), "CONSULTATION_MESSAGE_SENT",
                consultationId, doctorId, patientId, userId, OffsetDateTime.now());
    }
}
