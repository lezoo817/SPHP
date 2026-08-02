package com.sphp.patient.order.mapper;
/** 药房处方库存展示投影。 */
public record OrderPharmacyStockRecord(Long pharmacyId, String pharmacyName, Long hospitalId, Boolean isDefault,
                                       Long drugId, Integer availableCount, Integer unitPriceCent) { }
