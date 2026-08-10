package com.sphp.patient.order.mapper;
import java.time.OffsetDateTime;

/**
 * 药品订单支付记录
 * @param id 订单支付记录ID
 * @param drugOrderId 药品订单ID
 * @param patientId 患者ID
 * @param payerUserId 支付用户ID
 * @param pharmacyId 药房ID
 * @param paymentStatus 支付状态
 * @param orderStatus 订单状态
 * @param expireAt 过期时间
 * @param passwordHash 密码哈希
 */
public record DrugOrderPaymentRecord(Long id, Long drugOrderId, Long patientId, Long payerUserId, Long pharmacyId,
                                     String paymentStatus, String orderStatus, OffsetDateTime expireAt,
                                     String passwordHash) { }
