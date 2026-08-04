package com.sphp.patient.notification.mq.producer;

import com.sphp.patient.common.constant.NotificationConstant;
import com.sphp.patient.notification.mq.event.NotificationCreateEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import static com.sphp.patient.common.constant.NotificationConstant.*;

/** C端用药与随访提醒 RabbitMQ 生产器。 */
@Component
@RequiredArgsConstructor
public class NotificationReminderProducer {
    private final RabbitTemplate rabbitTemplate;

    /**
     * 发布到期用药提醒消息。
     *
     * @param event 用药提醒通知事件
     */
    public void publishMedicationReminder(NotificationCreateEvent event) {
        publish(event, REMINDER_DUE_ROUTING_KEY);
    }

    /**
     * 发布到期随访提醒消息。
     *
     * @param event 随访提醒通知事件
     */
    public void publishFollowUpReminder(NotificationCreateEvent event) {
        publish(event, FOLLOW_UP_DUE_ROUTING_KEY);
    }

    /**
     * 投递提醒消息到既定业务交换机。
     *
     * @param event 提醒通知事件
     * @param routingKey 既定提醒路由键
     */
    private void publish(NotificationCreateEvent event, String routingKey) {
        rabbitTemplate.convertAndSend(BUSINESS_EXCHANGE, routingKey, event);
    }
}
