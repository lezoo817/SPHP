package com.sphp.patient.order.mapper;
/** 购药处方药品投影。 */
public record OrderPrescriptionItemRecord(Long drugId, String drugName, Integer quantity, String dosage,
                                          String frequency, String usageMethod, Short days) { }
