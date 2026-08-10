package com.sphp.admin.doctor.mapper;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 号源时段信息（用于接诊时段校验）。
 *
 * @author lezoo17
 * @since 2026-08-10
 */
@Setter
@Getter
public class SlotTimeInfo {

    private LocalTime startTime;
    private LocalTime endTime;
    private LocalDate scheduleDate;

}