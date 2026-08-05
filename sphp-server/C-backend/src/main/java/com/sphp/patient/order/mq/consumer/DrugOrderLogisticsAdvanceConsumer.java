package com.sphp.patient.order.mq.consumer;

import com.sphp.patient.order.mq.event.DrugOrderLogisticsAdvanceEvent;
import com.sphp.patient.order.service.OrderLogisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static com.sphp.patient.common.constant.OrderConstant.DRUG_ORDER_LOGISTICS_ADVANCE_QUEUE;

/**
 * 购药订单模拟物流状态推进消费者。
 */
@Component
@RequiredArgsConstructor
public class DrugOrderLogisticsAdvanceConsumer {

    /** 物流状态推进服务 */
    private final OrderLogisticsService orderLogisticsService;

    /**
     * 消费延迟到期的物流推进消息。
     *
     * @param event 物流推进事件
     */
    @RabbitListener(queues = DRUG_ORDER_LOGISTICS_ADVANCE_QUEUE)
    public void consumeDrugOrderLogisticsAdvance(DrugOrderLogisticsAdvanceEvent event) {
        // 消费线程没有请求上下文，状态机仅以事件和数据库条件更新驱动。
        orderLogisticsService.advanceDrugOrderLogistics(event);
    }
}
