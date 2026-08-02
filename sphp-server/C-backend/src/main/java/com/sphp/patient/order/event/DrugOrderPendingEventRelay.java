package com.sphp.patient.order.event;
import com.sphp.patient.common.constant.OrderConstant;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
/** 购药订单待支付事件事务后投递器。 */
@Component @RequiredArgsConstructor
public class DrugOrderPendingEventRelay {
    private final RabbitTemplate rabbitTemplate;
    /** 事务提交后投递延迟消息，避免消费者读取已回滚订单。 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishDrugOrderPendingEvent(DrugOrderPendingEvent event) { rabbitTemplate.convertAndSend(OrderConstant.BUSINESS_EXCHANGE,OrderConstant.DRUG_ORDER_PENDING_ROUTING_KEY,event); }
}
