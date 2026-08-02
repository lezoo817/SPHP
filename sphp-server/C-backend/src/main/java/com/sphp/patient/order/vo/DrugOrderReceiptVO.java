package com.sphp.patient.order.vo;
import lombok.Builder;
import lombok.Getter;
import java.time.OffsetDateTime;
/** 确认收货结果。 */
@Getter @Builder public class DrugOrderReceiptVO { private final Long drugOrderId; private final String logisticsStatus; private final OffsetDateTime receivedAt; }
