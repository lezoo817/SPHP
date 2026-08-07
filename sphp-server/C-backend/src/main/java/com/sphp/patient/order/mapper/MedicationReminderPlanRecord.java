package com.sphp.patient.order.mapper;

/**
 * 购药订单关联的用药提醒计划投影。
 *
 * @param planId 用药计划 ID
 * @param frequency 支付时固化的处方频次
 */
public record MedicationReminderPlanRecord(Long planId, String frequency) {
}
