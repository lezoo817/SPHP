package com.sphp.patient.order.vo;
import lombok.Builder;
import lombok.Getter;
import java.time.OffsetDateTime;
/** 购药订单取消结果。 */
@Getter @Builder public class DrugOrderCancelVO { private final Long drugOrderId; private final String status; private final OffsetDateTime cancelledAt; }
