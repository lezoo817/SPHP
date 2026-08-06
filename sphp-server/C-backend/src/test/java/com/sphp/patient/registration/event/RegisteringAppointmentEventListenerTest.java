package com.sphp.patient.registration.event;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.OffsetDateTime;

import static com.sphp.patient.registration.config.RegisteringRabbitMqConfig.BUSINESS_EXCHANGE;
import static com.sphp.patient.registration.config.RegisteringRabbitMqConfig.LOCKED_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 挂号锁定延迟消息测试。
 */
class RegisteringAppointmentEventListenerTest {

    /**
     * 验证临近时段结束的锁号消息使用订单实际支付截止时间设置单消息 TTL。
     */
    @Test
    void registeringPublishLockedEventUsesActualPaymentDeadlineAsMessageTtl() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        RegisteringAppointmentEventListener listener = new RegisteringAppointmentEventListener(rabbitTemplate);
        RegisteringAppointmentLockedEvent event = RegisteringAppointmentLockedEvent.registeringOf(
                7001L, 10001L, OffsetDateTime.now().plusMinutes(5));
        org.mockito.ArgumentCaptor<MessagePostProcessor> postProcessorCaptor =
                org.mockito.ArgumentCaptor.forClass(MessagePostProcessor.class);

        listener.registeringPublishLockedEvent(event);

        verify(rabbitTemplate).convertAndSend(eq(BUSINESS_EXCHANGE), eq(LOCKED_KEY), eq(event),
                postProcessorCaptor.capture());
        Message message = new Message(new byte[0], new MessageProperties());
        Message processed = postProcessorCaptor.getValue().postProcessMessage(message);
        long expirationMillis = Long.parseLong(processed.getMessageProperties().getExpiration());
        assertSame(message, processed);
        assertTrue(expirationMillis > 0L);
        assertTrue(expirationMillis <= 300000L);
    }

    /**
     * 验证未指定实际截止时间的兼容事件继续使用延迟队列默认 TTL。
     */
    @Test
    void registeringPublishLockedEventKeepsQueueDefaultTtlWhenDeadlineIsAbsent() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        RegisteringAppointmentEventListener listener = new RegisteringAppointmentEventListener(rabbitTemplate);
        RegisteringAppointmentLockedEvent event = RegisteringAppointmentLockedEvent.registeringOf(7001L, 10001L);
        org.mockito.ArgumentCaptor<MessagePostProcessor> postProcessorCaptor =
                org.mockito.ArgumentCaptor.forClass(MessagePostProcessor.class);

        listener.registeringPublishLockedEvent(event);

        verify(rabbitTemplate).convertAndSend(eq(BUSINESS_EXCHANGE), eq(LOCKED_KEY), eq(event),
                postProcessorCaptor.capture());
        Message message = new Message(new byte[0], new MessageProperties());
        Message processed = postProcessorCaptor.getValue().postProcessMessage(message);
        assertEquals(null, processed.getMessageProperties().getExpiration());
    }
}
