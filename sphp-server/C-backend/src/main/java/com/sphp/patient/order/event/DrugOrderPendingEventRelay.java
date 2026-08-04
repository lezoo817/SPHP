package com.sphp.patient.order.event;
import com.sphp.patient.common.constant.OrderConstant;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static com.sphp.patient.common.constant.OrderConstant.BUSINESS_EXCHANGE;
import static com.sphp.patient.common.constant.OrderConstant.DRUG_ORDER_PENDING_ROUTING_KEY;

/** 购药订单待支付事件事务后投递器。 */
@Component @RequiredArgsConstructor
public class DrugOrderPendingEventRelay {

    private final RabbitTemplate rabbitTemplate;
    /** 事务提交后投递延迟消息，避免消费者读取已回滚订单。 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT) // 事务提交后执行
    public void publishDrugOrderPendingEvent(DrugOrderPendingEvent event) {
        rabbitTemplate.convertAndSend(
                BUSINESS_EXCHANGE,// 交换机
                DRUG_ORDER_PENDING_ROUTING_KEY, // 路由键
                event);
    }
}
