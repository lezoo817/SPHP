package com.sphp.patient.notification.mq.consumer;

import com.sphp.patient.notification.mapper.NotificationMapper;
import com.sphp.patient.notification.mq.event.MedicationReminderEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

import static com.sphp.patient.common.constant.NotificationConstant.REMINDER_QUEUE;

/** C端到期用药提醒消费者。 */
@Component
@RequiredArgsConstructor
public class MedicationReminderConsumer {
    // 通知创建消费者
    private final NotificationCreateConsumer notificationCreateConsumer;
    // 通知Mapper
    private final NotificationMapper notificationMapper;

    /**
     * 消费用药提醒并复用通知表幂等写入逻辑。
     *
     * @param event 用药提醒推进事件
     */
    @RabbitListener(queues = REMINDER_QUEUE)
    public void consumeMedicationReminder(MedicationReminderEvent event) {
        try {
            // 通知落库成功后才推进下一次提醒，避免消息失败造成提醒丢失。
            notificationCreateConsumer.persistNotification(event.toNotificationCreateEvent());
            notificationMapper.advanceMedicationReminder(event.planId(), event.dueAt(), event.nextRemindAt(),
                    OffsetDateTime.now());
        } catch (RuntimeException exception) {
            throw new AmqpRejectAndDontRequeueException("C端用药提醒消费失败", exception);
        }
    }
}
