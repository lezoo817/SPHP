package com.sphp.patient.order.vo;

import lombok.Builder;
import lombok.Getter;
import java.time.OffsetDateTime;
import java.util.List;

/** 购药订单分页响应。 */
@Getter @Builder
public class DrugOrderPageVO {
    /** 当前页码 */
    private final int pageNo;

    /** 当前页大小 */
    private final int pageSize;

    /** 总记录数 */
    private final long total;

    /** 订单列表 */
    private final List<Item> records;

    /** 订单列表项。 */
    @Getter
    @Builder
    public static class Item {
        // 订单 ID
        private final Long id;
        // 关联处方 ID，供客户端跳转处方对应的物流详情
        private final Long prescriptionId;
        // 订单名称
        private final String orderName;
        // 药房名称
        private final String pharmacyName;
        // 订单状态
        private final String status;
        // 物流状态
        private final String logisticsStatus;
        // 物流最新节点
        private final String latestLogisticsNode;
        // 订单金额，单位分
        private final Integer amountCent;
        // 支付到期时间
        private final OffsetDateTime expireAt;
        // 就诊人姓名
        private final String patientName;
    }
}
