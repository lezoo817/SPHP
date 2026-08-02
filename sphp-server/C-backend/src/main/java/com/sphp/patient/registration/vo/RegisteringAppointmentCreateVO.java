package com.sphp.patient.registration.vo;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * 挂号锁号成功响应对象。
 */
@Getter
@Builder
public class RegisteringAppointmentCreateVO {

    /** 挂号订单 ID */
    private final Long appointmentId;
    /** 挂号订单状态 */
    private final String status;
    /** 挂号金额，单位分 */
    private final Integer amountCent;
    /** 待支付到期时间 */
    private final OffsetDateTime expireAt;
    /** 关联支付单 ID */
    private final Long paymentId;
}
