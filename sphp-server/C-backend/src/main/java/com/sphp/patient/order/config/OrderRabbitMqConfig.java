package com.sphp.patient.order.config;
import com.sphp.patient.common.constant.OrderConstant;
import com.sphp.patient.registration.config.RegistrationProperties;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.sphp.patient.common.constant.OrderConstant.*;

/**
 * 订单 RabbitMQ 配置。
 * <p>
 *     使用死信队列模拟延时队列
 *   队列设 TTL → 无人消费 → 到期进 DLX → 转发到真正的处理队列 → 消费者执行超时逻辑。
 * </p>
 */
@Configuration
public class OrderRabbitMqConfig {
    /**
     * 创建购药延迟队列。
     * @param properties 挂号配置
     * @return 购药延迟队列
     */
    @Bean
    public Queue drugOrderDelayQueue(RegistrationProperties properties) {
        return QueueBuilder.durable(DRUG_ORDER_DELAY_QUEUE) // 创建队列
                .ttl(properties.getPaymentTimeout()*1000) // 设置队列超时时间
                .deadLetterExchange(DLX_EXCHANGE) // 设置队列的死信交换器
                .deadLetterRoutingKey(DRUG_ORDER_TIMEOUT_ROUTING_KEY) // 设置队列的死信路由
                .build();
    }

    /**
     * 创建购药超时队列。
     * @return 购药超时队列
     */
    @Bean
    public Queue drugOrderTimeoutQueue() {
        return QueueBuilder.durable(DRUG_ORDER_TIMEOUT_QUEUE)
                .build();
    }

    /**
     * 创建购药延迟队列绑定关系。
     * @param drugOrderDelayQueue 购药延迟队列
     * @return 购药延迟队列绑定关系
     */
    @Bean
    public Binding drugOrderDelayBinding(Queue drugOrderDelayQueue) {
        return BindingBuilder.bind(drugOrderDelayQueue) // 绑定队列
                .to(new TopicExchange(BUSINESS_EXCHANGE)) // 绑定交换器
                .with(DRUG_ORDER_PENDING_ROUTING_KEY); // 绑定路由
    }

    /**
     * 创建购药超时队列绑定关系。
     * @param drugOrderTimeoutQueue 购药超时队列
     * @return 购药超时队列绑定关系
     */
    @Bean
    public Binding drugOrderTimeoutBinding(Queue drugOrderTimeoutQueue) {
        return BindingBuilder.bind(drugOrderTimeoutQueue) // 绑定队列
                .to(new TopicExchange(DLX_EXCHANGE)) // 绑定交换器
                .with(DRUG_ORDER_TIMEOUT_ROUTING_KEY); // 绑定路由
    }
}
