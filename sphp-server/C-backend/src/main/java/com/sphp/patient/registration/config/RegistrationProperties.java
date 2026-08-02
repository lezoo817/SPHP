package com.sphp.patient.registration.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * C端挂号资源查询配置。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "sphp.registration")
public class RegistrationProperties {

    /** 挂号待支付窗口，单位秒，默认配置为 900 秒 */
    private int paymentTimeout;

    /** 可查询的未来放号天数，包含当天 */
    private int slotReleaseDays;
}
