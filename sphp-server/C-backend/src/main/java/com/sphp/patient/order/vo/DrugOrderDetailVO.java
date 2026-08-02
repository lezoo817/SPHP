package com.sphp.patient.order.vo;

import lombok.Builder;
import lombok.Getter;
import java.time.OffsetDateTime;
import java.util.List;

/** 购药订单详情响应。 */
@Getter @Builder
public class DrugOrderDetailVO {
    /** 订单 ID */ private final Long id;
    /** 订单状态 */ private final String status;
    /** 药房信息 */ private final Pharmacy pharmacy;
    /** 配送信息 */ private final Delivery delivery;
    /** 药品明细 */ private final List<Item> items;
    /** 订单金额，单位分 */ private final Integer amountCent;
    /** 支付信息 */ private final Payment payment;
    /** 药房信息。 */ @Getter @Builder public static class Pharmacy { private final Long id; private final String name; }
    /** 配送信息。 */ @Getter @Builder public static class Delivery { private final String method; private final String address; private final String company; private final String trackingNo; private final String logisticsStatus; private final List<Trace> traces; }
    /** 物流轨迹。 */ @Getter @Builder public static class Trace { private final String node; private final OffsetDateTime occurredAt; }
    /** 药品明细。 */ @Getter @Builder public static class Item { private final Long drugId; private final String drugName; private final Integer quantity; private final Integer unitPriceCent; }
    /** 支付信息。 */ @Getter @Builder public static class Payment { private final Long id; private final String status; }
}
