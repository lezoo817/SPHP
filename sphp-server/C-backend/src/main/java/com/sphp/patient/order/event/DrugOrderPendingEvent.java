package com.sphp.patient.order.event;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 购药订单创建后用于支付超时检查的领域事件。 */
public record DrugOrderPendingEvent(String eventId, String eventType, Long businessId, Long patientId, Long userId,
                                    OffsetDateTime occurredAt) implements Serializable {
    /** 创建购药订单待支付事件。 */
    public static DrugOrderPendingEvent of(Long drugOrderId, Long patientId, Long userId) {
        return new DrugOrderPendingEvent(UUID.randomUUID().toString(), "DRUG_ORDER_PENDING", drugOrderId, patientId, userId, OffsetDateTime.now());
    }
}
