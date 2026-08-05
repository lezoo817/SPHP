package com.sphp.patient.health.mapper;

import java.time.OffsetDateTime;

/**
 * 医生病历列表查询投影。
 *
 * @param id 问诊记录 ID，即病历 ID
 * @param patientId 就诊人 ID
 * @param doctorName 医生姓名
 * @param departmentName 科室名称
 * @param completedAt 问诊完成时间
 * @param updatedAt 病历最后保存时间
 */
public record ConsultationMedicalRecordListRecord(
        Long id,
        Long patientId,
        String doctorName,
        String departmentName,
        OffsetDateTime completedAt,
        OffsetDateTime updatedAt
) {
}
