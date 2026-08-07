package com.sphp.patient.order.vo;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * 购药订单收货后自动开启用药提醒的授权结果。
 */
@Getter
@Builder
public class DrugOrderReminderActivationVO {

    // 购药订单 ID
    private final Long drugOrderId;

    // 授权状态：PENDING_RECEIPT 或 ACTIVATED
    private final String status;

    // 授权登记时间
    private final OffsetDateTime authorizedAt;

    // 实际启用时间；待收货时为空
    private final OffsetDateTime activatedAt;
}
