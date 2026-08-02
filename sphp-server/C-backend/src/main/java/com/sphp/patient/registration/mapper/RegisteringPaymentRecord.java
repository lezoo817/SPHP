package com.sphp.patient.registration.mapper;
import java.time.OffsetDateTime;
/** 挂号支付单联表查询记录。 */
public record RegisteringPaymentRecord(Long id, Long appointmentId, Long patientId, Long payerUserId, Long snapshotId,
 Long slotId, Integer amountCent, String paymentStatus, String appointmentStatus, OffsetDateTime expireAt,
 OffsetDateTime paidAt, String passwordHash) { }
