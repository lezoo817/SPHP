package com.sphp.patient.order.support;

/**
 * 模拟配送距离与时效计算结果。
 *
 * @param distanceMeters 模拟配送距离，单位米
 * @param estimatedDeliveryMinutes 模拟预计配送时长，单位分钟
 */
public record DeliverySimulationResult(long distanceMeters, int estimatedDeliveryMinutes) {
}
