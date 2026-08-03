package com.sphp.admin.doctor.mapper;

import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 待接诊列表自定义查询结果行。
 */
@Data
public class QueueRow {

    private Long consultId;
    private Long patientId;
    private String patientName;
    private String patientGender;
    private LocalDate patientDateOfBirth;
    private String aiSummary;
    private OffsetDateTime appointmentTime;
    private String status;
    private Integer queueNumber;
}