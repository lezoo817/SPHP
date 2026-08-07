package com.sphp.patient.order.mq.consumer;

import com.sphp.patient.order.mq.event.DrugOrderLogisticsAdvanceEvent;
import com.sphp.patient.order.service.OrderLogisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static com.sphp.patient.common.constant.OrderConstant.DRUG_ORDER_LOGISTICS_ADVANCE_QUEUE;

/**
 * 购药订单模拟物流状态推进消费者。
 */
@Slf4j
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
        try {
            // 消费线程没有请求上下文，状态机仅以事件和数据库条件更新驱动。
            log.info("购药订单物流推进消息开始消费 eventId={}, drugOrderId={}, expectedLogisticsStatus={}, targetLogisticsStatus={}",
                    event == null ? null : event.eventId(), event == null ? null : event.drugOrderId(),
                    event == null ? null : event.expectedLogisticsStatus(),
                    event == null ? null : event.targetLogisticsStatus());
            orderLogisticsService.advanceDrugOrderLogistics(event);
            log.info("购药订单物流推进消息消费完成 eventId={}, drugOrderId={}",
                    event == null ? null : event.eventId(), event == null ? null : event.drugOrderId());
        } catch (RuntimeException exception) {
            // 显式拒绝且不重回队列，由队列死信配置保留失败消息供人工排查与补偿。
            log.error("购药订单物流推进消息消费失败 eventId={}, drugOrderId={}, expectedLogisticsStatus={}, targetLogisticsStatus={}",
                    event == null ? null : event.eventId(), event == null ? null : event.drugOrderId(),
                    event == null ? null : event.expectedLogisticsStatus(),
                    event == null ? null : event.targetLogisticsStatus(), exception);
            throw new AmqpRejectAndDontRequeueException("购药订单物流推进消息消费失败", exception);
        }
    }
}
