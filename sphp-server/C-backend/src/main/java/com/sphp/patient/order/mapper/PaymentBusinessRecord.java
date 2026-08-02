package com.sphp.patient.order.mapper;
/** 统一支付单业务归属投影。 */
public record PaymentBusinessRecord(Long id, Long appointmentId, Long drugOrderId) { }
