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

    /** 当前就诊人是否存在有效待就诊预约。 */
    private final boolean booked;
}
