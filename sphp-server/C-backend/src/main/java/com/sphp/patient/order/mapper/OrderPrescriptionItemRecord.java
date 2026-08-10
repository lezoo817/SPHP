package com.sphp.patient.order.mapper;

/**
 * 订单处方药品记录
 * @param drugId 药品ID
 * @param drugName 药品名称
 * @param quantity 数量
 * @param dosage 剂量
 * @param frequency 频率
 * @param usageMethod 使用方法
 * @param days 天数
 */
public record OrderPrescriptionItemRecord(Long drugId, String drugName, Integer quantity, String dosage,
                                          String frequency, String usageMethod, Short days) { }
