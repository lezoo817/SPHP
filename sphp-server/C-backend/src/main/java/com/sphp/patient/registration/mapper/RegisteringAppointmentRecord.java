package com.sphp.patient.registration.mapper;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
/** 挂号订单联表查询记录。 */
public record RegisteringAppointmentRecord(Long id, Long patientId, Long snapshotId, Long slotId, Long doctorId,
 String doctorName, String departmentName, String departmentLocation, LocalDate scheduleDate, LocalTime startTime, LocalTime endTime,
 String status, Integer amountCent, OffsetDateTime expireAt, Long paymentId, String paymentStatus) { }
