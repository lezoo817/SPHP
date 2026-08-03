package com.sphp.patient.notification.mq.producer;

import com.sphp.patient.common.enums.NotificationTypeEnum;
import com.sphp.patient.notification.mapper.NotificationMapper;
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
    private final NotificationMapper notificationMapper;

    /**
     * 发布待创建站内通知的本地领域事件。
     *
     * @param event 通知创建事件
     */
    public void publishNotificationCreate(NotificationCreateEvent event) {
        // 由事务后监听器投递 RabbitMQ，避免下游收到未提交业务事实。
        eventPublisher.publishEvent(event);
    }

    /**
     * 按业务资源定位信息创建并发布站内通知事件。
     *
     * @param eventType 事件类型
     * @param businessId 关联业务资源 ID
     * @param userId 通知接收用户 ID
     * @param patientId 可选关联就诊人 ID
     * @param type 通知展示类型
     * @param title 通知标题
     * @param content 通知正文
     */
    public void publishNotification(String eventType, Long businessId, Long userId, Long patientId,
                                    NotificationTypeEnum type, String title, String content) {
        // 读取名称快照后再发布，避免后续患者资料变更影响历史通知展示。
        String patientName = patientId == null ? null : notificationMapper.selectActivePatientName(patientId);
        publishNotificationCreate(NotificationCreateEvent.of(eventType, businessId, userId, patientId,
                patientName, type, title, content, "{}"));
    }
}
