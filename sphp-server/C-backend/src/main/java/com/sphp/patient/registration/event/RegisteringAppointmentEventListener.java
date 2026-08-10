package com.sphp.patient.registration.event;
import com.sphp.patient.registration.config.RegisteringRabbitMqConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.OffsetDateTime;

import static com.sphp.patient.common.constant.RegisteringConstant.BUSINESS_EXCHANGE;
import static com.sphp.patient.common.constant.RegisteringConstant.LOCKED_KEY;


/** 挂号锁定事件的事务后消息投递器。 */
@Component
@RequiredArgsConstructor
public class RegisteringAppointmentEventListener {

    private final RabbitTemplate rabbitTemplate;
    /** 仅在订单事务提交后投递延迟消息，避免消费者读取已回滚订单。 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)// 事务提交后执行
    public void registeringPublishLockedEvent(RegisteringAppointmentLockedEvent event) {
        rabbitTemplate.convertAndSend(
                BUSINESS_EXCHANGE,// 交换机
                LOCKED_KEY,  // 锁定路由键
                event,// 消息
                message -> {
                    registeringApplyPaymentDeadline(message.getMessageProperties(), event.expireAt());
                    return message;
                }
        );
    }

    /**
     * 为靠近时段结束的锁号缩短延迟消息存活时间。
     *
     * @param properties RabbitMQ 消息属性
     * @param expireAt 订单实际支付截止时间，可为空以复用队列默认超时
     */
    private void registeringApplyPaymentDeadline(MessageProperties properties, OffsetDateTime expireAt) {
        if (expireAt == null) {
            return;
        }
        // 单消息 TTL 与队列 900 秒 TTL 取较早者，保证临近时段结束的订单及时进入超时处理。
        long remainingMillis = Math.max(Duration.between(OffsetDateTime.now(), expireAt).toMillis(), 1L);
        properties.setExpiration(String.valueOf(remainingMillis));
    }
}
