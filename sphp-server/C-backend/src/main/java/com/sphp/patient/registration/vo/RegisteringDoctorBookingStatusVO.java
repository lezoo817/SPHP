package com.sphp.patient.registration.vo;

import lombok.Builder;
import lombok.Getter;

/**
 * 当前账号针对指定医生的成功预约状态响应。
 */
@Getter
@Builder
public class RegisteringDoctorBookingStatusVO {

    /** 医生 ID。 */
    private final Long doctorId;

    /** 当前账号任意有效就诊人是否已支付或完成预约。 */
    private final boolean booked;
}
