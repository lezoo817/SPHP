package com.sphp.patient.notification.mq.consumer;

import com.sphp.patient.common.constant.NotificationConstant;
import com.sphp.patient.notification.mq.event.NotificationCreateEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static com.sphp.patient.common.constant.NotificationConstant.FOLLOW_UP_QUEUE;

/** C端到期随访提醒消费者。 */
@Component
@RequiredArgsConstructor
public class FollowUpReminderConsumer {
    private final NotificationCreateConsumer notificationCreateConsumer;

    /**
     * 消费随访提醒并复用通知表幂等写入逻辑。
     *
     * @param event 随访提醒通知事件
     */
    @RabbitListener(queues = FOLLOW_UP_QUEUE)
    public void consumeFollowUpReminder(NotificationCreateEvent event) {
        // 复用通知表幂等写入逻辑
        notificationCreateConsumer.persistNotification(event);
    }
}
