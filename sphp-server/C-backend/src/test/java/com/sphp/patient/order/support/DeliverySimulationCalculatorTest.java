package com.sphp.patient.order.support;

import com.sphp.patient.common.enums.DeliveryProvinceEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模拟配送距离与时效计算器测试。
 */
class DeliverySimulationCalculatorTest {

    /**
     * 验证相同地址和医院的计算结果稳定，且同省配送时效固定在十五至十六小时。
     */
    @Test
    void deliveryCalculateKeepsSameProvinceResultStable() {
        DeliverySimulationCalculator calculator = new DeliverySimulationCalculator();

        DeliverySimulationResult first = calculator.deliveryCalculate(
                DeliveryProvinceEnum.HENAN, "郑州市高新区中心路1号2单元301", 1001L, 2001L, DeliveryProvinceEnum.HENAN, 0D);
        DeliverySimulationResult second = calculator.deliveryCalculate(
                DeliveryProvinceEnum.HENAN, "郑州市高新区中心路2号3单元502", 1001L, 2001L, DeliveryProvinceEnum.HENAN, 0D);

        assertEquals(first, second);
        assertTrue(first.distanceMeters() >= 5_000 && first.distanceMeters() <= 51_000);
        assertTrue(first.estimatedDeliveryMinutes() >= 900 && first.estimatedDeliveryMinutes() <= 960);
    }

    /**
     * 验证更大的跨省系数产生更长的模拟距离和配送时效。
     */
    @Test
    void deliveryCalculateIncreasesForLargerProvinceCoefficient() {
        DeliverySimulationCalculator calculator = new DeliverySimulationCalculator();

        DeliverySimulationResult near = calculator.deliveryCalculate(
                DeliveryProvinceEnum.HENAN, "郑州市中心路", 1001L, 2001L, DeliveryProvinceEnum.BEIJING, 1.0D);
        DeliverySimulationResult far = calculator.deliveryCalculate(
                DeliveryProvinceEnum.HENAN, "郑州市中心路", 1001L, 2001L, DeliveryProvinceEnum.GUANGDONG, 2.8D);

        assertTrue(far.distanceMeters() > near.distanceMeters());
        assertTrue(far.estimatedDeliveryMinutes() > near.estimatedDeliveryMinutes());
    }
}
