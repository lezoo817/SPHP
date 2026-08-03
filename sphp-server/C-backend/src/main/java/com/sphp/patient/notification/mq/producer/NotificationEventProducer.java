package com.sphp.patient.notification.mq.producer;

import com.sphp.patient.notification.mq.event.NotificationCreateEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * C端站内通知领域事件生产器。
 */
@Component
@RequiredArgsConstructor
public class NotificationEventProducer {

    private final ApplicationEventPublisher eventPublisher;

    /**
     * 发布待创建站内通知的本地领域事件。
     *
     * @param event 通知创建事件
     */
    public void publishNotificationCreate(NotificationCreateEvent event) {
        // 由事务后监听器投递 RabbitMQ，避免下游收到未提交业务事实。
        eventPublisher.publishEvent(event);
    }
}
