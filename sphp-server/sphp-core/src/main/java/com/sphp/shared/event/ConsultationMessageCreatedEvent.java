package com.sphp.shared.event;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 问诊文字消息持久化完成事件。
 *
 * <p>事件只传递定位和路由所需的元数据，医疗文字正文始终由接收端按消息 ID 从数据库读取。
 *
 * @param eventId 事件唯一 ID
 * @param messageId 消息 ID
 * @param consultationId 问诊记录 ID
 * @param senderType 发送方类型
 * @param doctorId 接诊医生 ID
 * @param patientId 就诊人 ID
 * @param occurredAt 消息创建时间
 */
public record ConsultationMessageCreatedEvent(String eventId, Long messageId, Long consultationId,
                                              String senderType, Long doctorId, Long patientId,
                                              OffsetDateTime occurredAt) implements java.io.Serializable {

    /**
     * 创建问诊消息持久化事件。
     *
     * @param messageId 消息 ID
     * @param consultationId 问诊记录 ID
     * @param senderType 发送方类型
     * @param doctorId 接诊医生 ID
     * @param patientId 就诊人 ID
     * @param occurredAt 消息创建时间
     * @return 不包含消息正文的事件
     */
    public static ConsultationMessageCreatedEvent of(Long messageId, Long consultationId, String senderType,
                                                      Long doctorId, Long patientId, OffsetDateTime occurredAt) {
        return new ConsultationMessageCreatedEvent(UUID.randomUUID().toString(), messageId, consultationId,
                senderType, doctorId, patientId, occurredAt);
    }
}
