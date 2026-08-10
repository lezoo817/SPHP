package com.sphp.patient.order.mapper;

/**
 * 支付业务记录
 * @param id 支付业务记录ID
 * @param appointmentId 预约ID
 * @param drugOrderId 药品订单ID
 */
public record PaymentBusinessRecord(Long id, Long appointmentId, Long drugOrderId) { }
