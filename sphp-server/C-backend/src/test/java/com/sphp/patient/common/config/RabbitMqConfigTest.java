package com.sphp.patient.common.config;

import com.sphp.patient.common.enums.DrugOrderLogisticsStatusEnum;
import com.sphp.patient.order.mq.event.DrugOrderLogisticsAdvanceEvent;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * RabbitMQ 消息转换器配置测试。
 */
class RabbitMqConfigTest {

    /**
     * 验证包含物流状态枚举的消息可在白名单约束下完成序列化和反序列化。
     */
    @Test
    void rabbitMessageConverterAllowsLogisticsStatusEnum() {
        RabbitMqConfig config = new RabbitMqConfig();
        MessageConverter converter = config.rabbitMessageConverter();
        DrugOrderLogisticsAdvanceEvent event = DrugOrderLogisticsAdvanceEvent.toInTransit(15001L);

        Message message = converter.toMessage(event, new MessageProperties());
        Object restored = converter.fromMessage(message);

        DrugOrderLogisticsAdvanceEvent restoredEvent = assertInstanceOf(DrugOrderLogisticsAdvanceEvent.class, restored);
        assertEquals(DrugOrderLogisticsStatusEnum.PENDING_SHIPMENT, restoredEvent.expectedLogisticsStatus());
        assertEquals(DrugOrderLogisticsStatusEnum.IN_TRANSIT, restoredEvent.targetLogisticsStatus());
    }
}
