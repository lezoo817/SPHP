package com.sphp.patient.order.mapper;

/**
 * 订单处方记录
 * @param id 订单处方记录ID
 * @param patientId 患者ID
 * @param hospitalId 医院ID
 * @param status 状态
 */
public record OrderPrescriptionRecord(Long id, Long patientId, Long hospitalId, String status) { }
