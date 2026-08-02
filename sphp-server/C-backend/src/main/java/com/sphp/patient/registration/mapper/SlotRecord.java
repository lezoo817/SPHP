package com.sphp.patient.registration.mapper;

import java.time.LocalTime;

/**
 * 已发布排班下的号源时段查询记录。
 *
 * @param slotId 时段 ID
 * @param startTime 时段开始时间
 * @param endTime 时段结束时间
 * @param feeCent 挂号费，单位分
 * @param availableCount PostgreSQL 快照中的可约数量
 * @param scheduleStatus 排班状态
 */
public record SlotRecord(Long slotId, LocalTime startTime, LocalTime endTime,
                         Integer feeCent, Long availableCount, String scheduleStatus) {
}
