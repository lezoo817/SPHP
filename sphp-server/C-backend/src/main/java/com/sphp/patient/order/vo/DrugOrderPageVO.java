package com.sphp.patient.order.vo;

import lombok.Builder;
import lombok.Getter;
import java.time.OffsetDateTime;
import java.util.List;

/** 购药订单分页响应。 */
@Getter @Builder
public class DrugOrderPageVO {
    /** 当前页码 */ private final int pageNo;
    /** 当前页大小 */ private final int pageSize;
    /** 总记录数 */ private final long total;
    /** 订单列表 */ private final List<Item> records;
    /** 订单列表项。 */
    @Getter @Builder public static class Item { private final Long id; private final String orderName; private final String pharmacyName; private final String status; private final String logisticsStatus; private final String latestLogisticsNode; private final Integer amountCent; private final OffsetDateTime expireAt; }
}
