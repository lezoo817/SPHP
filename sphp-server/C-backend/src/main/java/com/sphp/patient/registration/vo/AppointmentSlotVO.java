package com.sphp.patient.registration.vo;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * 可预约医生时段响应对象。
 */
@Getter
@Builder
public class AppointmentSlotVO {

    /** 时段 ID */
    private final Long slotId;

    /** 时段开始时间 */
    private final OffsetDateTime startTime;

    /** 时段结束时间 */
    private final OffsetDateTime endTime;

    /** 挂号费，单位分 */
    private final Integer feeCent;

    /** 实时可约号源数量 */
    private final Long availableCount;

    /** 排班状态，固定为 PUBLISHED */
    private final String scheduleStatus;

}
