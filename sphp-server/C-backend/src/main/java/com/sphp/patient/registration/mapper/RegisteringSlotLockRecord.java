package com.sphp.patient.registration.mapper;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 锁定挂号号源前的医院链路查询记录。
 *
 * @param slotId 时段 ID
 * @param hospitalId 医院 ID
 * @param doctorId 医生 ID
 * @param feeCent 挂号费用，单位分
 * @param scheduleDate 排班日期
 * @param startTime 时段开始时间
 * @param endTime 时段结束时间
 * @param scheduleStatus 排班状态
 * @param doctorStatus 医生状态
 */
public record RegisteringSlotLockRecord(Long slotId, Long hospitalId, Long doctorId, Integer feeCent,
                                        LocalDate scheduleDate, LocalTime startTime, LocalTime endTime,
                                        String scheduleStatus, String doctorStatus) {
}
