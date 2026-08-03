package com.sphp.patient.notification.mq.producer;

import com.sphp.patient.common.constant.NotificationConstant;
import com.sphp.patient.notification.mq.event.NotificationCreateEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static com.sphp.patient.common.constant.NotificationConstant.BUSINESS_EXCHANGE;
import static com.sphp.patient.common.constant.NotificationConstant.NOTIFICATION_CREATE_ROUTING_KEY;

/**
 * C端站内通知事件的事务后 RabbitMQ 投递器。
 */
@Component
@RequiredArgsConstructor
public class NotificationEventRelay {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 在业务事务提交后投递创建站内通知消息。
     *
     * @param event 通知创建事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT) // 事务提交后执行
    public void relayNotificationCreate(NotificationCreateEvent event) {
        // 投递创建站内通知消息
        rabbitTemplate.convertAndSend(BUSINESS_EXCHANGE,
                NOTIFICATION_CREATE_ROUTING_KEY, event);
    }
}
