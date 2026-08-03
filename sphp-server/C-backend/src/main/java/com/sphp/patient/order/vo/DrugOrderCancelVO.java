package com.sphp.patient.order.vo;
import lombok.Builder;
import lombok.Getter;
import java.time.OffsetDateTime;

/** 购药订单取消结果。 */
@Getter
@Builder
public class DrugOrderCancelVO {
    // 购药订单 ID
    private final Long drugOrderId;
    // 购药订单状态
    private final String status;
    // 取消时间
    private final OffsetDateTime cancelledAt;
}
