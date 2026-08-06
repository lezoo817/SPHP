package com.sphp.patient.health.vo;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * 医生病历详情响应。
 */
@Getter
@Builder
public class ProposalMedicalRecordDetailVO {
    // 问诊记录 ID，即病历 ID
    private final Long id;

    // 就诊人 ID
    private final Long patientId;

    // 医生 ID
    private final Long doctorId;

    // 医生姓名
    private final String doctorName;

    // 科室名称
    private final String departmentName;

    // 医生保存的病历正文
    private final String doctorNote;

    // 问诊开始时间
    private final OffsetDateTime startedAt;

    // 问诊完成时间
    private final OffsetDateTime completedAt;

    // 病历最后保存时间
    private final OffsetDateTime updatedAt;
}
