package com.sphp.patient.order.mapper;
import java.time.OffsetDateTime;
/** 购药支付单与订单归属投影。 */
public record DrugOrderPaymentRecord(Long id, Long drugOrderId, Long patientId, Long payerUserId, Long pharmacyId,
                                     String paymentStatus, String orderStatus, OffsetDateTime expireAt,
                                     String passwordHash) { }
