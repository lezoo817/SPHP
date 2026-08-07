package com.sphp.patient.order.mapper;

/**
 * 购药订单物流通知的接收目标投影。
 *
 * @param drugOrderId 购药订单 ID
 * @param patientId 关联就诊人 ID
 * @param payerUserId 支付并接收通知的 C端账号 ID
 */
public record DrugOrderNotificationTargetRecord(Long drugOrderId, Long patientId, Long payerUserId) {
}
