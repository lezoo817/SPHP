package com.sphp.patient.order.support;

/**
 * 购药订单创建时固化的配送信息。
 *
 * @param deliveryAddress 写入订单的不可变收货地址快照
 * @param estimatedDeliveryMinutes 基于地址与院内药房计算出的预计配送分钟数
 */
public record DeliveryOrderSnapshot(String deliveryAddress, int estimatedDeliveryMinutes) {
}
