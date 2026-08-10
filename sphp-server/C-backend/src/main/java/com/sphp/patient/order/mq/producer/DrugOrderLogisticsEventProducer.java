package com.sphp.patient.order.mq.producer;

import com.sphp.patient.order.mq.event.DrugOrderLogisticsAdvanceEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static com.sphp.patient.common.constant.OrderConstant.BUSINESS_EXCHANGE;
import static com.sphp.patient.common.constant.OrderConstant.DRUG_ORDER_LOGISTICS_SCHEDULE_ROUTING_KEY;

/**
 * 购药订单物流事件事务后消息投递器。
 */
@Component
@RequiredArgsConstructor
public class DrugOrderLogisticsEventProducer {

    /** RabbitMQ 消息模板 */
    private final RabbitTemplate rabbitTemplate;

    /**
     * 在订单事务提交后将物流推进事件投递到延迟队列。
     *
     * @param event 待延迟处理的物流推进事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT) // 事务提交后执行
    public void publishDrugOrderLogisticsEvent(DrugOrderLogisticsAdvanceEvent event) {
        // 仅在数据已提交后投递，避免消费者读取事务回滚的订单状态。
        rabbitTemplate.convertAndSend(BUSINESS_EXCHANGE, DRUG_ORDER_LOGISTICS_SCHEDULE_ROUTING_KEY, event);
    }
}
