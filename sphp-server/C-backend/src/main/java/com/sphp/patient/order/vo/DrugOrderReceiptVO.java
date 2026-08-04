package com.sphp.patient.order.vo;
import lombok.Builder;
import lombok.Getter;
import java.time.OffsetDateTime;

/** 确认收货结果。 */
@Getter
@Builder
public class DrugOrderReceiptVO {
    // 购药订单 ID
    private final Long drugOrderId;
    // 物流状态
    private final String logisticsStatus;
    // 收货时间
    private final OffsetDateTime receivedAt;

}
