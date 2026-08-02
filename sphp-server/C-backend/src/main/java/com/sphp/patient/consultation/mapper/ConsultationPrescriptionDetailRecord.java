package com.sphp.patient.consultation.mapper;

/**
 * 已批准处方详情投影。
 *
 * @param id 处方 ID
 * @param doctorId 开方医生 ID
 * @param doctorName 开方医生姓名
 * @param doctorTitle 开方医生职称
 */
public record ConsultationPrescriptionDetailRecord(Long id, Long doctorId, String doctorName, String doctorTitle) {
}
