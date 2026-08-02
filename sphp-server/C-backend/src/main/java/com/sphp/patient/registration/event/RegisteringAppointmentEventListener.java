package com.sphp.patient.registration.event;
import com.sphp.patient.registration.config.RegisteringRabbitMqConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
/** 挂号锁定事件的事务后消息投递器。 */
@Component @RequiredArgsConstructor
public class RegisteringAppointmentEventListener {
    private final RabbitTemplate rabbitTemplate;
    /** 仅在订单事务提交后投递延迟消息，避免消费者读取已回滚订单。 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void registeringPublishLockedEvent(RegisteringAppointmentLockedEvent event) { rabbitTemplate.convertAndSend(RegisteringRabbitMqConfig.BUSINESS_EXCHANGE, RegisteringRabbitMqConfig.LOCKED_KEY, event); }
}
