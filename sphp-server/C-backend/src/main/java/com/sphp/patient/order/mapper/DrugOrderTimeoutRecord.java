package com.sphp.patient.order.mapper;
/** 购药订单超时处理投影。 */
public record DrugOrderTimeoutRecord(Long drugOrderId, Long patientId, Long payerUserId, Long pharmacyId) { }
