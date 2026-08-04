package com.sphp.patient.consultation.event;

import com.sphp.patient.common.constant.ConsultationConstant;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static com.sphp.patient.common.constant.ConsultationConstant.BUSINESS_EXCHANGE;
import static com.sphp.patient.common.constant.ConsultationConstant.MESSAGE_SENT_ROUTING_KEY;

/**
 * 问诊消息事件的事务后 RabbitMQ 转发器。
 */
@Component
@RequiredArgsConstructor
public class ConsultationMessageEventRelay {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 在消息入库事务成功提交后转发事件，避免下游读取已回滚的消息。
     *
     * @param event 已持久化的问诊消息事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void relayConsultationMessageSentEvent(ConsultationMessageSentEvent event) {
        // 事件只携带业务定位字段，禁止将问诊文字原文放入消息队列。
        rabbitTemplate.convertAndSend(BUSINESS_EXCHANGE,
                MESSAGE_SENT_ROUTING_KEY, event);
    }
}
