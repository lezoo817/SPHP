package com.sphp.patient.order.mapper;

/**
 * 订单库存记录
 * @param pharmacyId 药房ID
 * @param pharmacyName 药房名称
 * @param drugId 药品ID
 * @param drugName 药品名称
 * @param quantity 数量
 * @param availableCount 可用数量
 * @param unitPriceCent 单价（分）
 * @param dosage 剂量
 * @param frequency 频率
 * @param usageMethod 使用方法
 * @param days 天数
 */
public record OrderStockRecord(Long pharmacyId, String pharmacyName, Long drugId, String drugName, Integer quantity,
                               Integer availableCount, Integer unitPriceCent, String dosage, String frequency,
                               String usageMethod, Short days) { }
