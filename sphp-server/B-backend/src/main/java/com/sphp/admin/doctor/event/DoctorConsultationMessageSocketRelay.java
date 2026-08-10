package com.sphp.admin.doctor.event;

import com.sphp.admin.auth.entity.BUser;
import com.sphp.admin.auth.mapper.BUserMapper;
import com.sphp.admin.doctor.entity.ConsultationMessage;
import com.sphp.admin.doctor.mapper.BConsultationMessageMapper;
import com.sphp.shared.event.ConsultationMessageCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 患者消息提交后向绑定医生推送实时通知。
 */
@Component
@RequiredArgsConstructor
public class DoctorConsultationMessageSocketRelay {

    /** 医生账号查询接口 */
    private final BUserMapper bUserMapper;
    /** 消息查询接口 */
    private final BConsultationMessageMapper messageMapper;
    /** STOMP 私有消息模板 */
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 在患者消息提交后读取正文并推送给接诊医生。
     *
     * @param event 已提交消息事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void relayPatientMessage(ConsultationMessageCreatedEvent event) {
        if (!"PATIENT".equals(event.senderType())) {
            return;
        }
        BUser doctorUser = bUserMapper.selectOne(com.baomidou.mybatisplus.core.toolkit.Wrappers.<BUser>lambdaQuery()
                .eq(BUser::getDoctorId, event.doctorId()).isNull(BUser::getDeletedAt));
        ConsultationMessage message = messageMapper.selectById(event.messageId());
        if (doctorUser == null || message == null || message.getDeletedAt() != null) {
            return;
        }
        // 消息正文只在提交后的本地推送中读取，事件本身不携带医疗文本。
        messagingTemplate.convertAndSendToUser("B:" + doctorUser.getId(), "/queue/consultation-message",
                new ConsultationMessagePush(event.consultationId(), message.getId(), message.getSenderType(),
                        message.getContent(), message.getCreatedAt()));
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
