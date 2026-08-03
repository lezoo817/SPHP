package com.sphp.patient.notification.mq.config;

import com.sphp.patient.common.constant.NotificationConstant;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.sphp.patient.common.constant.NotificationConstant.*;

/**
 * C端站内通知 RabbitMQ 队列与绑定配置。
 */
@Configuration
public class NotificationRabbitMqConfig {

    /**
     * 声明站内通知创建队列。
     *
     * @return 持久化通知队列
     */
    @Bean
    public Queue notificationQueue() {
        return createBusinessQueue(NOTIFICATION_QUEUE);
    }

    /**
     * 声明用药提醒队列。
     *
     * @return 持久化用药提醒队列
     */
    @Bean
    public Queue notificationReminderQueue() {

        return createBusinessQueue(REMINDER_QUEUE);
    }

    /**
     * 声明随访提醒队列。
     *
     * @return 持久化随访提醒队列
     */
    @Bean
    public Queue notificationFollowUpQueue() {
        return createBusinessQueue(FOLLOW_UP_QUEUE);
    }

    /**
     * 声明通知消费者失败后的死信队列。
     *
     * @return 持久化死信队列
     */
    @Bean
    public Queue notificationDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    /**
     * 将创建通知路由键绑定到通知队列。
     *
     * @param notificationQueue 通知队列
     * @return 队列绑定
     */
    @Bean
    public Binding notificationCreateBinding(Queue notificationQueue) {
        return bindBusinessQueue(notificationQueue, NOTIFICATION_CREATE_ROUTING_KEY);
    }

    /**
     * 将用药提醒路由键绑定到提醒队列。
     *
     * @param notificationReminderQueue 用药提醒队列
     * @return 队列绑定
     */
    @Bean
    public Binding notificationReminderBinding(Queue notificationReminderQueue) {
        return bindBusinessQueue(notificationReminderQueue, REMINDER_DUE_ROUTING_KEY);
    }

    /**
     * 将随访提醒路由键绑定到随访队列。
     *
     * @param notificationFollowUpQueue 随访提醒队列
     * @return 队列绑定
     */
    @Bean
    public Binding notificationFollowUpBinding(Queue notificationFollowUpQueue) {
        return bindBusinessQueue(notificationFollowUpQueue, FOLLOW_UP_DUE_ROUTING_KEY);
    }

    /**
     * 将通知消费失败消息路由到死信队列。
     *
     * @param notificationDeadLetterQueue 死信队列
     * @return 队列绑定
     */
    @Bean
    public Binding notificationDeadLetterBinding(Queue notificationDeadLetterQueue) {
        return BindingBuilder.bind(notificationDeadLetterQueue)
                .to(new TopicExchange(DLX_EXCHANGE))
                .with(DEAD_LETTER_ROUTING_KEY);
    }

    /**
     * 创建消费失败后进入死信交换机的业务队列。
     *
     * @param queueName 队列名称
     * @return 持久化业务队列
     */
    private Queue createBusinessQueue(String queueName) {
        return QueueBuilder.durable(queueName)
                .deadLetterExchange(DLX_EXCHANGE)
                .deadLetterRoutingKey(DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    /**
     * 绑定通知业务队列到既定业务交换机。
     *
     * @param queue 业务队列
     * @param routingKey 路由键
     * @return 队列绑定
     */
    private Binding bindBusinessQueue(Queue queue, String routingKey) {
        return BindingBuilder.bind(queue).to(new TopicExchange(BUSINESS_EXCHANGE)).with(routingKey);
    }
}
