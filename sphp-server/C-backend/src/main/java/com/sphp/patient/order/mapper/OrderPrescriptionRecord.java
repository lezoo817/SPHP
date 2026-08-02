package com.sphp.patient.order.mapper;
/** 购药处方资源投影。 */
public record OrderPrescriptionRecord(Long id, Long patientId, Long hospitalId, String status) { }
