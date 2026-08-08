package com.sphp.admin.doctor.event;

import com.sphp.shared.event.OnlineConsultationRepliedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static com.sphp.admin.common.constant.OnlineConsultationConstant.BUSINESS_EXCHANGE;
import static com.sphp.admin.common.constant.OnlineConsultationConstant.NOTIFICATION_CREATE_ROUTING_KEY;

/**
 * 在线问诊医生回复事件转发器。
 */
@Component
@RequiredArgsConstructor
public class OnlineConsultationReplyEventRelay {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 在回复事务提交后投递 C 端通知事件。
     *
     * @param event 在线问诊回复事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void relay(OnlineConsultationRepliedEvent event) {
        // 复用既有通知路由，避免新增同义交换机或队列。
        rabbitTemplate.convertAndSend(BUSINESS_EXCHANGE, NOTIFICATION_CREATE_ROUTING_KEY, event);
    }
}
