package com.sphp.patient.health.mapper;

import java.time.OffsetDateTime;

/**
 * 医生病历详情查询投影。
 *
 * @param id 问诊记录 ID，即病历 ID
 * @param patientId 就诊人 ID
 * @param doctorId 医生 ID
 * @param doctorName 医生姓名
 * @param departmentName 科室名称
 * @param doctorNote 医生保存的病历正文
 * @param startedAt 问诊开始时间
 * @param completedAt 问诊完成时间
 * @param updatedAt 病历最后保存时间
 */
public record ConsultationMedicalRecordRecord(
        // 问诊记录 ID，即病历 ID
        Long id,

        // 就诊人 ID
        Long patientId,

        // 医生 ID
        Long doctorId,

        // 医生姓名
        String doctorName,

        // 科室名称
        String departmentName,

        // 医生保存的病历正文
        String doctorNote,

        // 问诊开始时间
        OffsetDateTime startedAt,

        // 问诊完成时间
        OffsetDateTime completedAt,

        // 病历最后保存时间
        OffsetDateTime updatedAt
) {
}
