package com.sphp.patient.order.config;

import com.sphp.patient.common.enums.DeliveryProvinceEnum;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * C端模拟配送省市系数配置。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "sphp.delivery")
public class DeliveryProperties {

    /** 省市组合距离系数，键使用两个省市编码按字典序拼接。 */
    private Map<String, Double> provinceCoefficients = new HashMap<>();

    /**
     * 获取两个不同省市的配送距离系数。
     *
     * @param first 第一个省市
     * @param second 第二个省市
     * @return 已配置的正数距离系数；同省时返回零
     */
    public double deliveryProvinceCoefficient(DeliveryProvinceEnum first, DeliveryProvinceEnum second) {
        if (first == second) {
            return 0D;
        }
        Double coefficient = provinceCoefficients.get(deliveryCoefficientKey(first, second));
        if (coefficient == null || coefficient <= 0D) {
            throw new IllegalStateException("缺少模拟配送省市系数配置");
        }
        return coefficient;
    }

    /**
     * 生成对称省市组合的配置键。
     *
     * @param first 第一个省市
     * @param second 第二个省市
     * @return 小写且按字典序排列的配置键
     */
    private String deliveryCoefficientKey(DeliveryProvinceEnum first, DeliveryProvinceEnum second) {
        String firstCode = first.name().toLowerCase();
        String secondCode = second.name().toLowerCase();
        return firstCode.compareTo(secondCode) < 0 ? firstCode + "-" + secondCode : secondCode + "-" + firstCode;
    }
}
