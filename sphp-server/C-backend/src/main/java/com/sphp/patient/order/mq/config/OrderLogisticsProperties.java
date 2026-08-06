package com.sphp.patient.order.mq.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * C端购药订单模拟物流配置。
 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "sphp.order.logistics")
public class OrderLogisticsProperties {

    /** 每次自动推进物流状态的延迟秒数，默认 30 秒 */
    @Min(value = 1, message = "物流推进间隔必须大于零")
    private int advanceIntervalSeconds = 30;
}
