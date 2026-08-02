package com.sphp.patient.consultation.mapper;

/**
 * 已批准处方详情投影。
 *
 * @param id 处方 ID
 * @param doctorId 医生 ID
 * @param doctorName 医生名称
 * @param doctorTitle 医生职称
 */
public record PrescriptionDetailRecord(Long id, Long doctorId, String doctorName, String doctorTitle) {
}
