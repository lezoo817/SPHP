package com.sphp.patient.notification.mq;

import com.sphp.patient.common.enums.NotificationTypeEnum;
import com.sphp.patient.notification.mapper.NotificationMapper;
import com.sphp.patient.notification.mq.config.NotificationRabbitMqConfig;
import com.sphp.patient.notification.mq.consumer.NotificationCreateConsumer;
import com.sphp.patient.notification.mq.event.NotificationCreateEvent;
import com.sphp.patient.notification.mq.producer.NotificationEventRelay;
import com.sphp.patient.notification.mq.producer.NotificationReminderProducer;
import com.sphp.patient.notification.mq.scheduler.NotificationReminderScheduler;
import com.sphp.patient.notification.mapper.NotificationReminderRecord;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * C端站内通知 RabbitMQ 单元测试。
 */
class NotificationMqTest {

    /**
     * 验证通知创建消费者将事件转换为幂等通知写入。
     */
    @Test
    void notificationConsumerWritesEventIdempotently() {
        NotificationMapper mapper = mock(NotificationMapper.class);
        NotificationCreateConsumer consumer = new NotificationCreateConsumer(mapper);
        NotificationCreateEvent event = NotificationCreateEvent.of("APPOINTMENT_LOCKED", 30001L, 10001L,
                20001L, "张三", NotificationTypeEnum.APPOINTMENT, "挂号订单待支付", "请在规定时间内完成支付。", "{}");

        consumer.consumeNotificationCreate(event);
        consumer.consumeNotificationCreate(event);

        verify(mapper, times(2)).insertNotificationIfAbsent(org.mockito.ArgumentMatchers.argThat(notification ->
                event.eventId().equals(notification.getEventId()) && event.userId().equals(notification.getUserId())
                        && "APPOINTMENT".equals(notification.getType())));
    }

    /**
     * 验证事务后投递器使用既定交换机与通知创建路由键。
     */
    @Test
    void notificationRelayUsesEstablishedRoutingKey() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        NotificationEventRelay relay = new NotificationEventRelay(rabbitTemplate);
        NotificationCreateEvent event = NotificationCreateEvent.of("SYSTEM", 1L, 10001L, null, null,
                NotificationTypeEnum.SYSTEM, "系统通知", "通知内容", "{}");

        relay.relayNotificationCreate(event);

        verify(rabbitTemplate).convertAndSend("cend.business.exchange", "notification.create", event);
    }

    /**
     * 验证三条业务队列与死信队列使用既定队列名称。
     */
    @Test
    void notificationTopologyUsesEstablishedQueueNames() {
        NotificationRabbitMqConfig config = new NotificationRabbitMqConfig();
        Queue notificationQueue = config.notificationQueue();
        Queue reminderQueue = config.notificationReminderQueue();
        Queue followUpQueue = config.notificationFollowUpQueue();
        Queue deadLetterQueue = config.notificationDeadLetterQueue();
        Binding binding = config.notificationCreateBinding(notificationQueue);

        assertEquals("cend.notification.queue", notificationQueue.getName());
        assertEquals("cend.reminder.queue", reminderQueue.getName());
        assertEquals("cend.follow-up.queue", followUpQueue.getName());
        assertEquals("cend.dead-letter.queue", deadLetterQueue.getName());
        assertEquals("notification.create", binding.getRoutingKey());
    }

    /**
     * 验证到期扫描发送确定性事件 ID 的用药与随访提醒消息。
     */
    @Test
    void reminderSchedulerPublishesDueRemindersWithoutRequestContext() {
        NotificationMapper mapper = mock(NotificationMapper.class);
        NotificationReminderProducer producer = mock(NotificationReminderProducer.class);
        NotificationReminderScheduler scheduler = new NotificationReminderScheduler(mapper, producer);
        NotificationReminderRecord medication = reminderRecord(7001L, 10001L, 20001L);
        NotificationReminderRecord followUp = reminderRecord(8001L, 10001L, 20001L);
        when(mapper.selectDueMedicationReminders(any())).thenReturn(java.util.List.of(medication));
        when(mapper.selectDueFollowUpReminders(any())).thenReturn(java.util.List.of(followUp));

        scheduler.scanMedicationReminders();
        scheduler.scanFollowUpReminders();

        verify(producer).publishMedicationReminder(org.mockito.ArgumentMatchers.argThat(event ->
                event.eventId().startsWith("REMINDER:7001:") && "MEDICATION_REMINDER".equals(event.type())));
        verify(producer).publishFollowUpReminder(org.mockito.ArgumentMatchers.argThat(event ->
                event.eventId().startsWith("FOLLOW_UP:8001:") && "FOLLOW_UP_REMINDER".equals(event.type())));
    }

    /**
     * 创建到期提醒的查询投影。
     *
     * @param businessId 计划业务 ID
     * @param userId 接收用户 ID
     * @param patientId 就诊人 ID
     * @return 提醒查询投影
     */
    private NotificationReminderRecord reminderRecord(Long businessId, Long userId, Long patientId) {
        NotificationReminderRecord record = new NotificationReminderRecord();
        record.setBusinessId(businessId);
        record.setUserId(userId);
        record.setPatientId(patientId);
        record.setPatientName("张三");
        record.setDueAt(java.time.OffsetDateTime.now().minusMinutes(1));
        return record;
    }
}
