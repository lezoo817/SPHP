package com.sphp.patient.notification.mq.event;

import com.sphp.patient.common.enums.NotificationTypeEnum;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 写入 C端站内通知的统一消息事件。
 *
 * @param eventId 消息唯一 ID
 * @param eventType 事件类型
 * @param businessId 关联业务资源 ID
 * @param userId 通知接收用户 ID
 * @param patientId 可选关联就诊人 ID
 * @param patientName 就诊人名称快照
 * @param type 通知展示类型
 * @param title 通知标题
 * @param content 通知正文
 * @param payloadJson 脱敏业务扩展 JSON
 * @param occurredAt 事件发生时间
 */
public record NotificationCreateEvent(String eventId, String eventType, Long businessId, Long userId,
                                      Long patientId, String patientName, String type, String title,
                                      String content, String payloadJson, OffsetDateTime occurredAt) implements Serializable {

    /**
     * 创建一条通用站内通知事件。
     *
     * @param eventType 事件类型
     * @param businessId 关联业务资源 ID
     * @param userId 通知接收用户 ID
     * @param patientId 可选关联就诊人 ID
     * @param patientName 就诊人名称快照
     * @param type 通知展示类型
     * @param title 通知标题
     * @param content 通知正文
     * @param payloadJson 脱敏业务扩展 JSON
     * @return 新通知事件
     */
    public static NotificationCreateEvent of(String eventType, Long businessId, Long userId, Long patientId,
                                             String patientName, NotificationTypeEnum type, String title,
                                             String content, String payloadJson) {
        return new NotificationCreateEvent(UUID.randomUUID().toString(), eventType, businessId, userId, patientId,
                patientName, type.name(), title, content, payloadJson == null ? "{}" : payloadJson, OffsetDateTime.now());
    }
}
