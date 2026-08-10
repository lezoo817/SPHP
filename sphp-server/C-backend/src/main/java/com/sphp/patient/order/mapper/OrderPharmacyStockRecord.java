package com.sphp.patient.order.mapper;

/**
 * 药房库存记录
 * @param pharmacyId 药房ID
 * @param pharmacyName 药房名称
 * @param hospitalId 医院ID
 * @param isDefault 是否默认药房
 * @param drugId 药品ID
 * @param availableCount 可用数量
 * @param unitPriceCent 单价（分）
 */
public record OrderPharmacyStockRecord(Long pharmacyId, String pharmacyName, Long hospitalId, Boolean isDefault,
                                       Long drugId, Integer availableCount, Integer unitPriceCent) { }
