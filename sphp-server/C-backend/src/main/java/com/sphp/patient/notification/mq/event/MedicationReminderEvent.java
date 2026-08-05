package com.sphp.patient.notification.mq.event;

import com.sphp.patient.common.enums.NotificationTypeEnum;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * 到期用药提醒消息，携带推进下一次提醒所需的时间边界。
 *
 * @param eventId 消息唯一 ID
 * @param planId 用药计划 ID
 * @param userId 通知接收用户 ID
 * @param patientId 关联就诊人 ID
 * @param patientName 就诊人名称快照
 * @param dueAt 当前已到期的提醒时间
 * @param nextRemindAt 成功通知后应推进到的下次提醒时间
 * @param occurredAt 事件产生时间
 */
public record MedicationReminderEvent(String eventId, Long planId, Long userId, Long patientId, String patientName,
                                      OffsetDateTime dueAt, OffsetDateTime nextRemindAt,
                                      OffsetDateTime occurredAt) implements Serializable {

    /**
     * 转换为站内通知持久化事件。
     *
     * @return 固定展示文案的站内通知事件
     */
    public NotificationCreateEvent toNotificationCreateEvent() {
        return new NotificationCreateEvent(eventId, "MEDICATION_REMINDER_DUE", planId, userId, patientId,
                patientName, NotificationTypeEnum.MEDICATION_REMINDER.name(), "用药提醒",
                "您有一项用药计划需要按时完成。", "{}", occurredAt);
    }
}
