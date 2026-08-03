package com.sphp.patient.notification.mq;

import com.sphp.patient.common.enums.NotificationTypeEnum;
import com.sphp.patient.notification.mapper.NotificationMapper;
import com.sphp.patient.notification.mq.config.NotificationRabbitMqConfig;
import com.sphp.patient.notification.mq.consumer.NotificationCreateConsumer;
import com.sphp.patient.notification.mq.event.NotificationCreateEvent;
import com.sphp.patient.notification.mq.producer.NotificationEventRelay;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
}
