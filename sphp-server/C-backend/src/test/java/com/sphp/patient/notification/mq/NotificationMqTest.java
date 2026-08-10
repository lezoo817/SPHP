package com.sphp.patient.notification.mq;

import com.sphp.patient.common.config.RabbitMqConfig;
import com.sphp.patient.common.enums.NotificationTypeEnum;
import com.sphp.patient.notification.mapper.NotificationMapper;
import com.sphp.patient.notification.mq.config.NotificationRabbitMqConfig;
import com.sphp.patient.notification.mq.consumer.NotificationCreateConsumer;
import com.sphp.patient.notification.mq.event.NotificationCreateEvent;
import com.sphp.patient.notification.mq.event.MedicationReminderEvent;
import com.sphp.patient.notification.mq.producer.NotificationEventRelay;
import com.sphp.patient.notification.mq.producer.NotificationReminderProducer;
import com.sphp.patient.notification.mq.scheduler.NotificationReminderScheduler;
import com.sphp.patient.notification.mapper.NotificationReminderRecord;
import com.sphp.patient.notification.mapper.OnlineConsultationNotificationRecord;
import com.sphp.shared.event.OnlineConsultationRepliedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        NotificationCreateConsumer consumer = new NotificationCreateConsumer(mapper, messageConverter());
        NotificationCreateEvent event = NotificationCreateEvent.of("APPOINTMENT_LOCKED", 30001L, 10001L,
                20001L, "张三", NotificationTypeEnum.APPOINTMENT, "挂号订单待支付", "请在规定时间内完成支付。", "{}");

        consumer.dispatchNotificationEvent(event);
        consumer.dispatchNotificationEvent(event);

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
     * 验证到期扫描为已开启提醒的用药计划发送确定性推进事件。
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
                event.eventId().startsWith("REMINDER:7001:")
                        && event.nextRemindAt().isAfter(event.dueAt())));
        verify(producer).publishFollowUpReminder(org.mockito.ArgumentMatchers.argThat(event ->
                event.eventId().startsWith("FOLLOW_UP:8001:") && "FOLLOW_UP_REMINDER".equals(event.type())));
    }

    /**
     * 验证在线问诊通知不保存医疗正文，并使用共享事件 ID 幂等写入。
     */
    @Test
    void onlineConsultationReplyCreatesConsultationNotificationFromDatabase() {
        NotificationMapper mapper = mock(NotificationMapper.class);
        MessageConverter converter = messageConverter();
        NotificationCreateConsumer consumer = new NotificationCreateConsumer(mapper, converter);
        OnlineConsultationNotificationRecord record = new OnlineConsultationNotificationRecord();
        record.setUserId(10001L);
        record.setPatientId(20001L);
        record.setPatientName("张三");
        record.setContent("请按处方用药并注意休息");
        when(mapper.selectOnlineConsultationNotifications(11001L)).thenReturn(java.util.List.of(record));
        OnlineConsultationRepliedEvent event = new OnlineConsultationRepliedEvent(
                "ONLINE_CONSULTATION_REPLIED:11001", 11001L, 20001L, 30001L, OffsetDateTime.now());

        // 监听器先接收原始 Message，再使用统一转换器恢复共享事件类型。
        consumer.consumeNotificationCreate(converter.toMessage(event, new MessageProperties()));

        verify(mapper).insertNotificationIfAbsent(org.mockito.ArgumentMatchers.argThat(notification ->
                "CONSULTATION".equals(notification.getType())
                        && event.eventId().equals(notification.getEventId())
                        && notification.getPayload().contains("\"consultationId\":11001")
                        && "请进入在线问诊查看医生消息".equals(notification.getContent())));
    }

    /**
     * 验证用药提醒通知落库后按原到期时间条件推进下一次提醒。
     */
    @Test
    void medicationReminderConsumerAdvancesNextReminderAfterPersistingNotification() {
        NotificationMapper mapper = mock(NotificationMapper.class);
        NotificationCreateConsumer notificationConsumer = new NotificationCreateConsumer(mapper, messageConverter());
        com.sphp.patient.notification.mq.consumer.MedicationReminderConsumer consumer =
                new com.sphp.patient.notification.mq.consumer.MedicationReminderConsumer(notificationConsumer, mapper);
        OffsetDateTime dueAt = OffsetDateTime.parse("2026-08-05T08:00:00+08:00");
        OffsetDateTime nextRemindAt = OffsetDateTime.parse("2026-08-05T14:00:00+08:00");
        MedicationReminderEvent event = new MedicationReminderEvent("REMINDER:7001:1", 7001L, 10001L, 20001L,
                "张三", dueAt, nextRemindAt, OffsetDateTime.now());

        consumer.consumeMedicationReminder(event);

        verify(mapper).insertNotificationIfAbsent(org.mockito.ArgumentMatchers.argThat(notification ->
                "REMINDER:7001:1".equals(notification.getEventId()) && notification.getUserId().equals(10001L)));
        verify(mapper).advanceMedicationReminder(eq(7001L), eq(dueAt), eq(nextRemindAt), any());
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
        record.setReminderTimesJson("[\"08:00\",\"14:00\",\"20:00\"]");
        return record;
    }

    /**
     * 创建与生产配置一致的 RabbitMQ 消息转换器。
     *
     * @return 支持项目业务事件白名单的转换器
     */
    private MessageConverter messageConverter() {
        return new RabbitMqConfig().rabbitMessageConverter();
    }
}
