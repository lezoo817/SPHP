package com.sphp.patient.registration.config;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
/** C端挂号支付超时 RabbitMQ 拓扑配置。 */
@Configuration
public class RegisteringRabbitMqConfig {
    /** 业务交换机名称。 */
    public static final String BUSINESS_EXCHANGE = "cend.business.exchange";

    /** 死信交换机名称。 */
    public static final String DLX_EXCHANGE = "cend.dlx.exchange";

    /** 挂号延迟队列名称。 */
    public static final String DELAY_QUEUE = "cend.appointment.delay.queue";

    /** 挂号超时队列名称。 */
    public static final String TIMEOUT_QUEUE = "cend.appointment.timeout.queue";

    /** 锁号路由键。 */
    public static final String LOCKED_KEY = "appointment.locked";

    /** 超时路由键。 */
    public static final String TIMEOUT_KEY = "appointment.timeout";

    /** 声明业务 Topic 交换机。 */
    @Bean
    public TopicExchange registeringBusinessExchange() {
        return new TopicExchange(BUSINESS_EXCHANGE, true, false);
    }
    /** 声明死信 Topic 交换机。 */
    @Bean
    public TopicExchange registeringDlxExchange() {
        return new TopicExchange(DLX_EXCHANGE, true, false);
    }
    /** 声明 15 分钟后死信的挂号延迟队列。 */
    @Bean
    public Queue registeringDelayQueue(RegistrationProperties p) {
        return QueueBuilder.durable(DELAY_QUEUE)
                .ttl(p.getPaymentTimeout() * 1000) // 15 分钟后死信
                .deadLetterExchange(DLX_EXCHANGE). // 死信交换机
                deadLetterRoutingKey(TIMEOUT_KEY) // 死信路由
                .build();
    }
    /** 声明挂号超时消费者队列。 */
    @Bean
    public Queue registeringTimeoutQueue() {
        return QueueBuilder.durable(TIMEOUT_QUEUE).build();
    }
    /** 绑定锁号消息到延迟队列。 */
    @Bean
    public Binding registeringDelayBinding(@Qualifier("registeringDelayQueue") Queue registeringDelayQueue,
                                           @Qualifier("registeringBusinessExchange") TopicExchange registeringBusinessExchange) {
        return BindingBuilder.bind(registeringDelayQueue) // 绑定队列
                .to(registeringBusinessExchange) // 绑定交换机
                .with(LOCKED_KEY);  // 绑定路由
    }
    /** 绑定死信超时消息到消费者队列。 */
    @Bean
    public Binding registeringTimeoutBinding(@Qualifier("registeringTimeoutQueue") Queue registeringTimeoutQueue,
                                             @Qualifier("registeringDlxExchange") TopicExchange registeringDlxExchange) {
        return BindingBuilder.bind(registeringTimeoutQueue)
                .to(registeringDlxExchange)
                .with(TIMEOUT_KEY);
    }
}
