package com.sphp.patient.notification.mq.consumer;

import com.sphp.patient.common.constant.NotificationConstant;
import com.sphp.patient.notification.entity.Notification;
import com.sphp.patient.notification.mapper.NotificationMapper;
import com.sphp.patient.notification.mapper.OnlineConsultationNotificationRecord;
import com.sphp.patient.notification.mq.event.NotificationCreateEvent;
import com.sphp.shared.event.OnlineConsultationRepliedEvent;
import com.sphp.shared.event.ConsultationMessageCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.MessageConverter;
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
    private final MessageConverter messageConverter;

    /**
     * 消费通知创建事件并幂等写入 C端通知表。
     *
     * @param message RabbitMQ 原始消息
     */
    @RabbitListener(queues = NOTIFICATION_QUEUE)
    public void consumeNotificationCreate(Message message) {
        try {
            // Object 形参会让监听器保留原始 Message，必须显式使用统一转换器恢复业务事件。
            Object event = messageConverter.fromMessage(message);
            dispatchNotificationEvent(event);
        } catch (RuntimeException exception) {
            log.error("C端通知消费失败 contentType={}", message.getMessageProperties().getContentType(), exception);
            throw new AmqpRejectAndDontRequeueException("C端通知消费失败", exception);
        }
    }

    /**
     * 按实际业务事件类型分发通知创建逻辑。
     *
     * @param event 反序列化后的业务事件
     */
    public void dispatchNotificationEvent(Object event) {
        if (event instanceof NotificationCreateEvent notificationEvent) {
            persistNotification(notificationEvent);
            return;
        }
        if (event instanceof OnlineConsultationRepliedEvent repliedEvent) {
            persistOnlineConsultationNotifications(repliedEvent);
            return;
        }
        if (event instanceof ConsultationMessageCreatedEvent messageEvent && "DOCTOR".equals(messageEvent.senderType())) {
            persistOnlineConsultationMessageNotifications(messageEvent);
            return;
        }
        throw new IllegalArgumentException("不支持的通知事件类型");
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
            notification.setTitle("医生发来在线问诊消息");
            notification.setContent("请进入在线问诊查看医生消息");
            notification.setPayload("{\"consultationId\":" + event.consultationId() + "}");
            notification.setEventId(event.eventId());
            inserted += notificationMapper.insertNotificationIfAbsent(notification);
        }
        return inserted;
    }

    /**
     * 根据医生实时消息为患者账号创建不包含医疗原文的问诊通知。
     *
     * @param event 已持久化医生消息事件
     * @return 实际插入通知数量
     */
    public int persistOnlineConsultationMessageNotifications(ConsultationMessageCreatedEvent event) {
        if (event.eventId() == null || event.messageId() == null || event.consultationId() == null
                || event.patientId() == null) {
            throw new IllegalArgumentException("在线问诊消息事件字段不完整");
        }
        int inserted = 0;
        for (OnlineConsultationNotificationRecord record
                : notificationMapper.selectOnlineConsultationNotifications(event.consultationId())) {
            Notification notification = new Notification();
            notification.setUserId(record.getUserId());
            notification.setPatientId(record.getPatientId());
            notification.setPatientNameSnapshot(record.getPatientName());
            notification.setType(CONSULTATION.name());
            notification.setTitle("医生发来在线问诊消息");
            notification.setContent("请进入在线问诊查看医生消息");
            notification.setPayload("{\"consultationId\":" + event.consultationId()
                    + ",\"messageId\":" + event.messageId() + "}");
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
