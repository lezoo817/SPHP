package com.sphp.patient.order.vo;

import lombok.Builder;
import lombok.Getter;
import java.time.OffsetDateTime;
import java.util.List;

/** 购药订单详情响应。 */
@Getter
@Builder
public class DrugOrderDetailVO {
    /** 订单 ID */
    private final Long id;

    /** 关联处方 ID，用于客户端恢复购药流程上下文。 */
    private final Long prescriptionId;

    /** 订单状态 */
    private final String status;

    /** 药房信息 */
    private final Pharmacy pharmacy;

    /** 配送信息 */
    private final Delivery delivery;

    /** 药品明细 */
    private final List<Item> items;

    /** 订单金额，单位分 */
    private final Integer amountCent;

    /** 支付信息 */
    private final Payment payment;

    /** 药房信息。 */
    @Getter
    @Builder
    public static class Pharmacy {
        // 药房 ID
        private final Long id;
        //
        private final String name;
    }

    /** 配送信息。 */
    @Getter
    @Builder
    public static class Delivery {
        // 配送方式
        private final String method;
        // 收货地址
        private final String address;
        // 物流公司
        private final String company;
        // 物流单号
        private final String trackingNo;
        // 物流状态
        private final String logisticsStatus;
        // 物流轨迹
        private final List<Trace> traces;
    }
    /** 物流轨迹。 */
    @Getter
    @Builder
    public static class Trace {
        // 节点名称
        private final String node;
        // 节点描述
        private final OffsetDateTime occurredAt;
    }

    /** 药品明细。 */
    @Getter
    @Builder
    public static class Item {
        // 药品 ID
        private final Long drugId;
        // 药品名称
        private final String drugName;
        // 购买数量
        private final Integer quantity;
        // 采购单价，单位分
        private final Integer unitPriceCent;
    }

    /** 支付信息。 */
    @Getter
    @Builder
    public static class Payment {
        // 支付单 ID
        private final Long id;
        // 支付金额，单位分
        private final String status;
    }
}
