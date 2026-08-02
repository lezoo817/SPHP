package com.sphp.patient.order.vo;

import lombok.Builder;
import lombok.Getter;
import java.time.OffsetDateTime;
import java.util.List;

/** 创建购药订单结果。 */
@Getter @Builder
public class DrugOrderCreateVO {
    /** 购药订单 ID */ private final Long drugOrderId;
    /** 订单状态 */ private final String status;
    /** 配送方式 */ private final String deliveryMethod;
    /** 订单金额，单位分 */ private final Integer amountCent;
    /** 支付到期时间 */ private final OffsetDateTime expireAt;
    /** 支付单 ID */ private final Long paymentId;
    /** 订单药品摘要 */ private final List<Item> items;
    /** 订单药品摘要项。 */
    @Getter @Builder public static class Item { private final Long drugId; private final String drugName; private final Integer quantity; }
}
