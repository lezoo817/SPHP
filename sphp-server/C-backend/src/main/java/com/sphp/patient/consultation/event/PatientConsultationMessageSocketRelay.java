package com.sphp.patient.consultation.event;

import com.sphp.patient.consultation.entity.ConsultationMessage;
import com.sphp.patient.consultation.mapper.ConsultationDataMapper;
import com.sphp.patient.consultation.mapper.ConsultationMessageMapper;
import com.sphp.shared.event.ConsultationMessageCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.OffsetDateTime;

/**
 * 医生消息提交后向拥有患者关系的 C 端账号推送实时通知。
 */
@Component
@RequiredArgsConstructor
public class PatientConsultationMessageSocketRelay {

    /** 患者关系查询接口 */
    private final ConsultationDataMapper consultationDataMapper;
    /** 消息查询接口 */
    private final ConsultationMessageMapper messageMapper;
    /** STOMP 私有消息模板 */
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 在医生消息事务提交后读取正文并推送给患者关联账号。
     *
     * @param event 已提交消息事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void relayDoctorMessage(ConsultationMessageCreatedEvent event) {
        if (!"DOCTOR".equals(event.senderType())) {
            return;
        }
        ConsultationMessage message = messageMapper.selectById(event.messageId());
        if (message == null || message.getDeletedAt() != null) {
            return;
        }
        ConsultationMessagePush payload = new ConsultationMessagePush(event.consultationId(), message.getId(),
                message.getSenderType(), message.getContent(), message.getCreatedAt());
        // 同一患者的有效授权账号均可查看该问诊，全部使用私有用户队列接收推送。
        consultationDataMapper.selectConsultationPatientUserIds(event.patientId()).forEach(userId ->
                messagingTemplate.convertAndSendToUser("C:" + userId, "/queue/consultation-message", payload));
    }

    /**
     * 实时消息推送载荷。
     *
     * @param consultationId 问诊记录 ID
     * @param messageId 消息 ID
     * @param senderType 发送方类型
     * @param content 文字内容
     * @param createdAt 创建时间
     */
    public record ConsultationMessagePush(Long consultationId, Long messageId, String senderType,
                                          String content, OffsetDateTime createdAt) { }
}
