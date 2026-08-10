package com.sphp.patient.order.mapper;
import java.time.OffsetDateTime;

/**
 * 订单物流轨迹记录
 * @param node 节点
 * @param occurredAt 发生时间
 */
public record OrderTraceRecord(String node, OffsetDateTime occurredAt) { }
