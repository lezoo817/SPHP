package com.sphp.admin.doctor.mapper;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 号源时段信息（用于接诊时段校验）。
 */
public class SlotTimeInfo {

    private LocalTime startTime;
    private LocalTime endTime;
    private LocalDate scheduleDate;

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
    public LocalDate getScheduleDate() { return scheduleDate; }
    public void setScheduleDate(LocalDate scheduleDate) { this.scheduleDate = scheduleDate; }
}