package com.sphp.patient.registration.vo;
import lombok.Builder;
import lombok.Getter;
import java.time.OffsetDateTime;

/** 模拟支付成功响应对象。 */
@Getter
@Builder
public class RegisteringPaymentSuccessVO {

    /** 支付单 ID */
    private final Long paymentId;

    /** 支付状态 */
    private final String status;

    /** 付款时间 */
    private final OffsetDateTime paidAt;
}
