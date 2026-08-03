package com.sphp.patient.order.mapper;
import java.time.OffsetDateTime;
/** 购药订单列表投影。 */
public record OrderListRecord(Long id, String orderName, String pharmacyName, String status, String logisticsStatus,
                              String latestLogisticsNode, Integer amountCent, OffsetDateTime expireAt) { }
