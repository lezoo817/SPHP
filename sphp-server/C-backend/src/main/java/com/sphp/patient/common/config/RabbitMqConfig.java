package com.sphp.patient.common.config;

import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 消息转换器配置。
 * Spring AMQP 3.x 默认只允许反序列化白名单内的类（如 java.util.*、java.lang.*），
 * 业务自定义事件类必须显式加入允许列表，否则消费端反序列化时会抛出
 * SecurityException。该转换器兼容原有 JDK 序列化消息，可直接复用。
 * 注意：AllowedListDeserializingMessageConverter 是抽象基类，
 * 其白名单方法 addAllowedListPatterns() 由其子类 SimpleMessageConverter 继承使用。
 */
@Configuration
public class RabbitMqConfig {

    /**
     * 基于 JDK 序列化的消息转换器，并放行本项目事件类。
     * <p>
     * Spring Boot 会自动将该唯一 MessageConverter 应用到 RabbitTemplate
     * 与 @RabbitListener 容器工厂，生产与消费两端同时生效。
     *
     * @return 带反序列化白名单的消息转换器
     */
    @Bean
    public MessageConverter rabbitMessageConverter() {
        SimpleMessageConverter converter = new SimpleMessageConverter();
        // 放行本项目事件类（NotificationCreateEvent、ConsultationMessageSentEvent 等）
        // 以及事件字段中时间类型（OffsetDateTime 等）JDK 序列化使用的内部代理类 java.time.Ser
        // 物流事件使用受限状态枚举，显式放行 JDK Enum 基类而不扩大为全局信任。
        converter.addAllowedListPatterns("com.sphp.patient.**", "java.time.**", "java.lang.Enum");
        return converter;
    }
}
