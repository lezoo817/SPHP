package com.sphp.patient.notification.mq.consumer;

import com.sphp.patient.common.constant.NotificationConstant;
import com.sphp.patient.notification.entity.Notification;
import com.sphp.patient.notification.mapper.NotificationMapper;
import com.sphp.patient.notification.mapper.OnlineConsultationNotificationRecord;
import com.sphp.patient.notification.mq.event.NotificationCreateEvent;
import com.sphp.shared.event.OnlineConsultationRepliedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static com.sphp.patient.common.constant.NotificationConstant.NOTIFICATION_QUEUE;
import static com.sphp.patient.common.enums.NotificationTypeEnum.CONSULTATION;

/**
 * C端站内通知创建消费者。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationCreateConsumer {

    private final NotificationMapper notificationMapper;

    /**
     * 消费通知创建事件并幂等写入 C端通知表。
     *
     * @param event 通知创建事件或在线问诊回复事件
     */
    @RabbitListener(queues = NOTIFICATION_QUEUE)
    public void consumeNotificationCreate(Object event) {
        try {
            if (event instanceof NotificationCreateEvent notificationEvent) {
                persistNotification(notificationEvent);
                return;
            }
            if (event instanceof OnlineConsultationRepliedEvent repliedEvent) {
                persistOnlineConsultationNotifications(repliedEvent);
                return;
            }
            throw new IllegalArgumentException("不支持的通知事件类型");
        } catch (RuntimeException exception) {
            log.error("C端通知消费失败 eventType={}", event == null ? null : event.getClass().getName(), exception);
            throw new AmqpRejectAndDontRequeueException("C端通知消费失败", exception);
        }
    }

    /**
     * 根据在线问诊回复事件为本人账号幂等创建通知。
     *
     * @param event 在线问诊回复事件
     * @return 实际插入通知数量
     */
    public int persistOnlineConsultationNotifications(OnlineConsultationRepliedEvent event) {
        if (event == null || event.eventId() == null || event.eventId().isBlank()
                || event.consultationId() == null || event.patientId() == null) {
            throw new IllegalArgumentException("在线问诊回复事件字段不完整");
        }
        int inserted = 0;
        // 回复正文只从已提交数据库事实读取，不通过 RabbitMQ 传播。
        for (OnlineConsultationNotificationRecord record
                : notificationMapper.selectOnlineConsultationNotifications(event.consultationId())) {
            Notification notification = new Notification();
            notification.setUserId(record.getUserId());
            notification.setPatientId(record.getPatientId());
            notification.setPatientNameSnapshot(record.getPatientName());
            notification.setType(CONSULTATION.name());
            notification.setTitle("医生已回复在线问诊");
            notification.setContent(record.getContent());
            notification.setPayload("{\"consultationId\":" + event.consultationId() + "}");
            notification.setEventId(event.eventId());
            inserted += notificationMapper.insertNotificationIfAbsent(notification);
        }
        return inserted;
    }

    /**
     * 校验并幂等持久化一条站内通知，供其他消费者复用。
     *
     * @param event 通知创建事件
     * @return 首次插入时为 1，重复事件时为 0
     */
    public int persistNotification(NotificationCreateEvent event) {
        // 校验消息
        validateEvent(event);
        Notification notification = new Notification();
        notification.setUserId(event.userId());
        notification.setPatientId(event.patientId());
        notification.setPatientNameSnapshot(event.patientName());
        notification.setType(event.type());
        notification.setTitle(event.title());
        notification.setContent(event.content());
        notification.setPayload(event.payloadJson());
        notification.setEventId(event.eventId());
        // eventId 与 userId 的唯一索引消除生产重试和重复投递造成的重复通知。
        return notificationMapper.insertNotificationIfAbsent(notification);
    }

    /**
     * 校验消息具备持久化所需的最小安全字段。
     *
     * @param event 通知创建事件
     */
    private void validateEvent(NotificationCreateEvent event) {
        if (event == null || event.eventId() == null || event.eventId().isBlank() || event.userId() == null
                || event.type() == null || event.type().isBlank() || event.title() == null || event.title().isBlank()
                || event.content() == null || event.content().isBlank()) {
            throw new IllegalArgumentException("通知消息字段不完整");
        }
    }
}
