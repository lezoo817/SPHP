package com.sphp.patient.notification.mq.scheduler;

import com.sphp.patient.common.enums.NotificationTypeEnum;
import com.sphp.patient.notification.mapper.NotificationMapper;
import com.sphp.patient.notification.mapper.NotificationReminderRecord;
import com.sphp.patient.notification.mq.event.MedicationReminderEvent;
import com.sphp.patient.notification.mq.event.NotificationCreateEvent;
import com.sphp.patient.notification.mq.producer.NotificationReminderProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

import static com.sphp.patient.common.enums.NotificationTypeEnum.FOLLOW_UP_REMINDER;
import static com.sphp.patient.common.enums.NotificationTypeEnum.MEDICATION_REMINDER;
import static com.sphp.patient.health.support.ProposalMedicationReminderSupport.proposalCalculateNextReminderAt;
import static com.sphp.patient.health.support.ProposalMedicationReminderSupport.proposalParseReminderTimes;

/** C端用药和随访提醒到期扫描任务。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationReminderScheduler {
    //消息映射器
    private final NotificationMapper notificationMapper;
    //提醒消息生产器
    private final NotificationReminderProducer notificationReminderProducer;

    /** 扫描到期用药计划并发送提醒消息。 */
    @Scheduled(fixedDelayString = "${sphp.notification.scan-interval-millis}")//间隔毫秒数
    public void scanMedicationReminders() {
        OffsetDateTime now = OffsetDateTime.now();
        notificationMapper.selectDueMedicationReminders(now).forEach(record ->
                notificationReminderProducer.publishMedicationReminder(buildMedicationReminderEvent(record)));
    }

    /**
     * 组装包含当前到期和下一次提醒时间的用药提醒事件。
     *
     * @param record 到期用药计划投影
     * @return 可推进下一次提醒的消息事件
     */
    private MedicationReminderEvent buildMedicationReminderEvent(NotificationReminderRecord record) {
        OffsetDateTime nextRemindAt = proposalCalculateNextReminderAt(
                proposalParseReminderTimes(record.getReminderTimesJson()), record.getDueAt());
        String eventId = "REMINDER:" + record.getBusinessId() + ":"
                + record.getDueAt().toInstant().toEpochMilli();
        log.info("创建了一个用药提醒: {}", eventId);
        return new MedicationReminderEvent(eventId, record.getBusinessId(), record.getUserId(), record.getPatientId(),
                record.getPatientName(), record.getDueAt(), nextRemindAt, OffsetDateTime.now());
    }

    /** 扫描到期随访计划并发送提醒消息。 */
    @Scheduled(fixedDelayString = "${sphp.notification.scan-interval-millis}") // 间隔毫秒数
    public void scanFollowUpReminders() {
        OffsetDateTime now = OffsetDateTime.now();
        notificationMapper.selectDueFollowUpReminders(now).forEach(record ->
                notificationReminderProducer.publishFollowUpReminder(buildReminderEvent(record, "FOLLOW_UP",
                        "FOLLOW_UP_REMINDER_DUE", FOLLOW_UP_REMINDER,
                        "随访提醒", "您有一项随访计划需要按时确认或完成。")));
    }

    /**
     * 将到期计划投影转换为不携带病历原文的提醒通知事件。
     *
     * @param record 到期计划通知投影
     * @param eventPrefix 事件 ID 前缀
     * @param eventType 事件类型
     * @param type 通知展示类型
     * @param title 通知标题
     * @param content 通知正文
     * @return 可被 RabbitMQ 投递的提醒事件
     */
    private NotificationCreateEvent buildReminderEvent(NotificationReminderRecord record, String eventPrefix,
                                                       String eventType, NotificationTypeEnum type,
                                                       String title, String content) {
        // 计划 ID 和提醒时间共同确定事件 ID，扫描重试和多实例竞态均可被通知唯一索引去重。
        String eventId = eventPrefix + ":" + record.getBusinessId() + ":" + record.getDueAt().toInstant().toEpochMilli();
        return new NotificationCreateEvent(eventId, eventType, record.getBusinessId(), record.getUserId(),
                record.getPatientId(), record.getPatientName(), type.name(), title, content, "{}", OffsetDateTime.now());
    }
}
