package com.sphp.patient.order.mq.event;

import com.sphp.patient.common.enums.DrugOrderLogisticsStatusEnum;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;
@Slf4j
/**
 * 购药订单模拟物流状态推进事件。
 */
public record DrugOrderLogisticsAdvanceEvent(String eventId, Long drugOrderId,
                                             DrugOrderLogisticsStatusEnum expectedLogisticsStatus,
                                             DrugOrderLogisticsStatusEnum targetLogisticsStatus,
                                             OffsetDateTime occurredAt) implements Serializable {

    /**
     * 创建待发货进入运输中的延迟事件。
     *
     * @param drugOrderId 购药订单 ID
     * @return 物流推进事件
     */
    public static DrugOrderLogisticsAdvanceEvent toInTransit(Long drugOrderId) {
        log.info("物流状态推进事件 创建待发货进入运输中 {}", drugOrderId);
        return create(drugOrderId, DrugOrderLogisticsStatusEnum.PENDING_SHIPMENT,
                DrugOrderLogisticsStatusEnum.IN_TRANSIT);
    }

    /**
     * 创建运输中进入待收货的延迟事件。
     *
     * @param drugOrderId 购药订单 ID
     * @return 物流推进事件
     */
    public static DrugOrderLogisticsAdvanceEvent toReceive(Long drugOrderId) {
        log.info("物流状态推进事件 创建运输中进入待收货 {}", drugOrderId);
        return create(drugOrderId, DrugOrderLogisticsStatusEnum.IN_TRANSIT,
                DrugOrderLogisticsStatusEnum.TO_RECEIVE);
    }

    /**
     * 创建带唯一事件标识的物流推进事件。
     *
     * @param drugOrderId 购药订单 ID
     * @param expectedLogisticsStatus 期望物流状态
     * @param targetLogisticsStatus 目标物流状态
     * @return 物流推进事件
     */
    private static DrugOrderLogisticsAdvanceEvent create(Long drugOrderId,
                                                           DrugOrderLogisticsStatusEnum expectedLogisticsStatus,
                                                           DrugOrderLogisticsStatusEnum targetLogisticsStatus) {
        return new DrugOrderLogisticsAdvanceEvent(UUID.randomUUID().toString(), drugOrderId,
                expectedLogisticsStatus, targetLogisticsStatus, OffsetDateTime.now());
    }
}
