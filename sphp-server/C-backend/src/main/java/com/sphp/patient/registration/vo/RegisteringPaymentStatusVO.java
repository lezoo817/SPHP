package com.sphp.patient.registration.vo;
import lombok.Builder;
import lombok.Getter;
import java.time.OffsetDateTime;
/** 挂号支付状态响应对象。 */
@Getter @Builder public class RegisteringPaymentStatusVO { /** 支付单 ID */ private final Long id; /** 挂号订单 ID */ private final Long appointmentOrderId; /** 金额，单位分 */ private final Integer amountCent; /** 支付状态 */ private final String status; /** 付款时间 */ private final OffsetDateTime paidAt; /** 到期时间 */ private final OffsetDateTime expireAt; }
