package com.sphp.patient.order.mapper;

/**
 * 自动用药提醒授权时锁定的购药订单投影。
 *
 * @param drugOrderId 购药订单 ID
 * @param patientId 订单所属就诊人 ID
 * @param orderStatus 订单状态
 * @param logisticsStatus 物流状态
 */
public record DrugOrderReminderOrderRecord(Long drugOrderId, Long patientId, String orderStatus,
                                            String logisticsStatus) {
}
