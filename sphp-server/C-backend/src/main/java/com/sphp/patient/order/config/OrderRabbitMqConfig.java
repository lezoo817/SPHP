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
/** C端购药订单超时 RabbitMQ 拓扑配置。 */
@Configuration
public class OrderRabbitMqConfig {
    /** 声明购药待支付延迟队列。 */
    @Bean public Queue drugOrderDelayQueue(RegistrationProperties properties) { return QueueBuilder.durable(OrderConstant.DRUG_ORDER_DELAY_QUEUE).ttl(properties.getPaymentTimeout()*1000).deadLetterExchange(OrderConstant.DLX_EXCHANGE).deadLetterRoutingKey(OrderConstant.DRUG_ORDER_TIMEOUT_ROUTING_KEY).build(); }
    /** 声明购药超时消费者队列。 */
    @Bean public Queue drugOrderTimeoutQueue() { return QueueBuilder.durable(OrderConstant.DRUG_ORDER_TIMEOUT_QUEUE).build(); }
    /** 将待支付事件绑定到延迟队列。 */
    @Bean public Binding drugOrderDelayBinding(Queue drugOrderDelayQueue) { return BindingBuilder.bind(drugOrderDelayQueue).to(new TopicExchange(OrderConstant.BUSINESS_EXCHANGE)).with(OrderConstant.DRUG_ORDER_PENDING_ROUTING_KEY); }
    /** 将死信超时事件绑定到超时队列。 */
    @Bean public Binding drugOrderTimeoutBinding(Queue drugOrderTimeoutQueue) { return BindingBuilder.bind(drugOrderTimeoutQueue).to(new TopicExchange(OrderConstant.DLX_EXCHANGE)).with(OrderConstant.DRUG_ORDER_TIMEOUT_ROUTING_KEY); }
}
