package com.sphp.patient.health.mapper;

import java.time.OffsetDateTime;

/**
 * 医生病历报告详情查询投影。
 *
 * @param id 问诊记录 ID，即 C 端报告 ID
 * @param patientId 就诊人 ID
 * @param doctorId 医生 ID
 * @param doctorName 医生姓名
 * @param departmentName 科室名称
 * @param doctorNote 医生病历正文
 * @param startedAt 问诊开始时间
 * @param completedAt 问诊完成时间
 * @param updatedAt 病历最后保存时间
 */
public record ConsultationReportRecord(
        Long id,
        Long patientId,
        Long doctorId,
        String doctorName,
        String departmentName,
        String doctorNote,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        OffsetDateTime updatedAt
) {
}
