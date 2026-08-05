package com.sphp.patient.order.mq;

import com.sphp.patient.order.mq.config.OrderLogisticsProperties;
import com.sphp.patient.order.mq.config.OrderLogisticsRabbitMqConfig;
import com.sphp.patient.order.mq.consumer.DrugOrderLogisticsAdvanceConsumer;
import com.sphp.patient.order.mq.event.DrugOrderLogisticsAdvanceEvent;
import com.sphp.patient.order.mq.producer.DrugOrderLogisticsEventProducer;
import com.sphp.patient.order.service.OrderLogisticsService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 购药订单模拟物流 RabbitMQ 测试。
 */
class OrderLogisticsMqTest {

    /**
     * 验证物流延迟队列、推进队列和路由键使用既定名称及 30 秒延迟。
     */
    @Test
    void logisticsTopologyUsesExpectedQueuesAndDelay() {
        OrderLogisticsProperties properties = new OrderLogisticsProperties();
        properties.setAdvanceIntervalSeconds(30);
        OrderLogisticsRabbitMqConfig config = new OrderLogisticsRabbitMqConfig();
        Queue delayQueue = config.drugOrderLogisticsDelayQueue(properties);
        Queue advanceQueue = config.drugOrderLogisticsAdvanceQueue();
        Binding delayBinding = config.drugOrderLogisticsDelayBinding(delayQueue);
        Binding advanceBinding = config.drugOrderLogisticsAdvanceBinding(advanceQueue);

        assertEquals("cend.drug-order.logistics.delay.queue", delayQueue.getName());
        assertEquals(30000, delayQueue.getArguments().get("x-message-ttl"));
        assertEquals("cend.drug-order.logistics.advance.queue", advanceQueue.getName());
        assertEquals("drug-order.logistics.schedule", delayBinding.getRoutingKey());
        assertEquals("drug-order.logistics.advance", advanceBinding.getRoutingKey());
    }

    /**
     * 验证事务后投递器向物流延迟路由发送事件。
     */
    @Test
    void logisticsProducerUsesDelayRoutingKey() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        DrugOrderLogisticsEventProducer producer = new DrugOrderLogisticsEventProducer(rabbitTemplate);
        DrugOrderLogisticsAdvanceEvent event = DrugOrderLogisticsAdvanceEvent.toInTransit(15001L);

        producer.publishDrugOrderLogisticsEvent(event);

        verify(rabbitTemplate).convertAndSend("cend.business.exchange", "drug-order.logistics.schedule", event);
    }

    /**
     * 验证消费者只将事件交给物流服务处理，不依赖请求线程用户上下文。
     */
    @Test
    void logisticsConsumerDelegatesWithoutRequestContext() {
        OrderLogisticsService service = mock(OrderLogisticsService.class);
        DrugOrderLogisticsAdvanceConsumer consumer = new DrugOrderLogisticsAdvanceConsumer(service);
        DrugOrderLogisticsAdvanceEvent event = DrugOrderLogisticsAdvanceEvent.toReceive(15001L);

        consumer.consumeDrugOrderLogisticsAdvance(event);

        verify(service).advanceDrugOrderLogistics(event);
    }
}
