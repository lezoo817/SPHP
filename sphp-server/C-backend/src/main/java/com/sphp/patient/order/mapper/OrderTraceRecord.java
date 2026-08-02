package com.sphp.patient.order.mapper;
import java.time.OffsetDateTime;
/** 购药订单物流轨迹投影。 */
public record OrderTraceRecord(String node, OffsetDateTime occurredAt) { }
