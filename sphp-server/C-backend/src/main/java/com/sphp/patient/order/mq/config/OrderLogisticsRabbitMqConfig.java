package com.sphp.patient.order.mq.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.sphp.patient.common.constant.OrderConstant.BUSINESS_EXCHANGE;
import static com.sphp.patient.common.constant.OrderConstant.DLX_EXCHANGE;
import static com.sphp.patient.common.constant.OrderConstant.DRUG_ORDER_LOGISTICS_ADVANCE_QUEUE;
import static com.sphp.patient.common.constant.OrderConstant.DRUG_ORDER_LOGISTICS_ADVANCE_ROUTING_KEY;
import static com.sphp.patient.common.constant.OrderConstant.DRUG_ORDER_LOGISTICS_DELAY_QUEUE;
import static com.sphp.patient.common.constant.OrderConstant.DRUG_ORDER_LOGISTICS_SCHEDULE_ROUTING_KEY;

/**
 * C端购药订单模拟物流 RabbitMQ 队列与绑定配置。
 */
@Configuration
public class OrderLogisticsRabbitMqConfig {

    /**
     * 创建物流延迟队列，消息到期后进入物流推进队列。
     *
     * @param properties 物流推进间隔配置
     * @return 持久化物流延迟队列
     */
    @Bean
    public Queue drugOrderLogisticsDelayQueue(OrderLogisticsProperties properties) {
        return QueueBuilder.durable(DRUG_ORDER_LOGISTICS_DELAY_QUEUE)
                // 使用固定延迟模拟药房处理和配送运输进度。
                .ttl(Math.toIntExact(properties.getAdvanceIntervalSeconds() * 1000L))
                .deadLetterExchange(DLX_EXCHANGE)
                .deadLetterRoutingKey(DRUG_ORDER_LOGISTICS_ADVANCE_ROUTING_KEY)
                .build();
    }

    /**
     * 创建物流状态推进消费队列。
     *
     * @return 持久化物流推进队列
     */
    @Bean
    public Queue drugOrderLogisticsAdvanceQueue() {
        return QueueBuilder.durable(DRUG_ORDER_LOGISTICS_ADVANCE_QUEUE).build();
    }

    /**
     * 绑定物流延迟投递路由。
     *
     * @param drugOrderLogisticsDelayQueue 物流延迟队列
     * @return 业务交换机绑定关系
     */
    @Bean
    public Binding drugOrderLogisticsDelayBinding(Queue drugOrderLogisticsDelayQueue) {
        return BindingBuilder.bind(drugOrderLogisticsDelayQueue)
                .to(new TopicExchange(BUSINESS_EXCHANGE))
                .with(DRUG_ORDER_LOGISTICS_SCHEDULE_ROUTING_KEY);
    }

    /**
     * 绑定延迟到期后的物流推进路由。
     *
     * @param drugOrderLogisticsAdvanceQueue 物流推进队列
     * @return 死信交换机绑定关系
     */
    @Bean
    public Binding drugOrderLogisticsAdvanceBinding(Queue drugOrderLogisticsAdvanceQueue) {
        return BindingBuilder.bind(drugOrderLogisticsAdvanceQueue)
                .to(new TopicExchange(DLX_EXCHANGE))
                .with(DRUG_ORDER_LOGISTICS_ADVANCE_ROUTING_KEY);
    }
}
