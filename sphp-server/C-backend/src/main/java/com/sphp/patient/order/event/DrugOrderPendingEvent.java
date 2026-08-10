package com.sphp.patient.order.event;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 药品订单待支付事件
 * @param eventId 事件ID
 * @param eventType 事件类型
 * @param businessId 业务ID
 * @param patientId 患者ID
 * @param userId 用户ID
 * @param occurredAt 发生时间
 */
public record DrugOrderPendingEvent(String eventId, String eventType, Long businessId, Long patientId, Long userId,
                                    OffsetDateTime occurredAt) implements Serializable {
    /** 创建购药订单待支付事件。 */
    public static DrugOrderPendingEvent of(Long drugOrderId, Long patientId, Long userId) {
        return new DrugOrderPendingEvent(UUID.randomUUID().toString(),
                "DRUG_ORDER_PENDING",
                drugOrderId,
                patientId,
                userId,
                OffsetDateTime.now());
    }
}
