package com.sphp.patient.notification.mq.consumer;

import com.sphp.patient.common.constant.NotificationConstant;
import com.sphp.patient.notification.mq.event.NotificationCreateEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** C端到期用药提醒消费者。 */
@Component
@RequiredArgsConstructor
public class MedicationReminderConsumer {
    private final NotificationCreateConsumer notificationCreateConsumer;

    /**
     * 消费用药提醒并复用通知表幂等写入逻辑。
     *
     * @param event 用药提醒通知事件
     */
    @RabbitListener(queues = NotificationConstant.REMINDER_QUEUE)
    public void consumeMedicationReminder(NotificationCreateEvent event) {
        notificationCreateConsumer.consumeNotificationCreate(event);
    }
}
