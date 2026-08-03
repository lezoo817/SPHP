package com.sphp.patient.order.event;
import com.sphp.patient.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
/** 购药订单支付超时消费者。 */
@Component @RequiredArgsConstructor
public class DrugOrderTimeoutConsumer {
    private final OrderService orderService;
    /** 仅处理仍待支付的订单，重复消息不会重复释放库存。 */
    @RabbitListener(queues = "cend.drug-order.timeout.queue") // 监听超时队列
    public void consumeDrugOrderTimeout(DrugOrderPendingEvent event) {
        orderService.expireDrugOrder(event.businessId()); // 释放库存
    }
}
