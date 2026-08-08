package com.sphp.shared.event;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 在线问诊医生回复完成事件。
 *
 * <p>事件仅携带业务定位字段，不携带回复原文；C 端消费者根据问诊 ID 从数据库读取回复内容。
 *
 * @param eventId 事件唯一 ID
 * @param consultationId 问诊记录 ID
 * @param patientId 患者 ID
 * @param doctorId 医生 ID
 * @param occurredAt 回复完成时间
 */
public record OnlineConsultationRepliedEvent(String eventId, Long consultationId, Long patientId,
                                             Long doctorId, OffsetDateTime occurredAt) implements Serializable {

    /**
     * 创建在线问诊医生回复完成事件。
     *
     * @param consultationId 问诊记录 ID
     * @param patientId 患者 ID
     * @param doctorId 医生 ID
     * @param occurredAt 回复完成时间
     * @return 在线问诊医生回复完成事件
     */
    public static OnlineConsultationRepliedEvent of(Long consultationId, Long patientId, Long doctorId,
                                                    OffsetDateTime occurredAt) {
        return new OnlineConsultationRepliedEvent(UUID.randomUUID().toString(), consultationId, patientId,
                doctorId, occurredAt);
    }
}
