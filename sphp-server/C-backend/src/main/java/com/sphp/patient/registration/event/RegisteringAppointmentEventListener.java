package com.sphp.patient.registration.event;
import com.sphp.patient.registration.config.RegisteringRabbitMqConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static com.sphp.patient.registration.config.RegisteringRabbitMqConfig.BUSINESS_EXCHANGE;
import static com.sphp.patient.registration.config.RegisteringRabbitMqConfig.LOCKED_KEY;

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
                event// 消息
        );

    }
}
