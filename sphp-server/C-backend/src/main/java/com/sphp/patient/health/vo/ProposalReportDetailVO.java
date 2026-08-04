package com.sphp.patient.health.vo;
import lombok.*;
import java.time.OffsetDateTime;

/**
 * 医生病历报告详情响应。
 */
@Getter
@Builder
public class ProposalReportDetailVO {
    // 报告 ID，即问诊记录 ID
    private final Long id;
    // 就诊人 ID
    private final Long patientId;
    // 医生 ID
    private final Long doctorId;
    // 医生姓名
    private final String doctorName;
    // 科室名称
    private final String departmentName;
    // 医生病历正文
    private final String doctorNote;
    // 问诊开始时间
    private final OffsetDateTime startedAt;
    // 问诊完成时间
    private final OffsetDateTime completedAt;
    // 病历最后保存时间
    private final OffsetDateTime updatedAt;
}
