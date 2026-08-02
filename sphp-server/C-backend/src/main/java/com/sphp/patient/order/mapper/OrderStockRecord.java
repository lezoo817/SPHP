package com.sphp.patient.order.mapper;
/** 下单时的药房库存锁定投影。 */
public record OrderStockRecord(Long pharmacyId, String pharmacyName, Long drugId, String drugName, Integer quantity,
                               Integer availableCount, Integer unitPriceCent, String dosage, String frequency,
                               String usageMethod, Short days) { }
