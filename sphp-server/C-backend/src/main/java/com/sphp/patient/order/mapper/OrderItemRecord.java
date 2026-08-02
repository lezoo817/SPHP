package com.sphp.patient.order.mapper;
/** 购药订单明细投影。 */
public record OrderItemRecord(Long drugId, String drugName, Integer quantity, Integer unitPriceCent) { }
