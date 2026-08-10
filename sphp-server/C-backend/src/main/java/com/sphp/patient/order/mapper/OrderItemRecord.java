package com.sphp.patient.order.mapper;

/**
 * 订单明细记录
 * @param drugId 药品ID
 * @param drugName 药品名称
 * @param quantity 数量
 * @param unitPriceCent 单价（分）
 */
public record OrderItemRecord(Long drugId, String drugName, Integer quantity, Integer unitPriceCent) { }
