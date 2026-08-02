package com.sphp.patient.consultation.mapper;

/**
 * 处方资源归属投影。
 *
 * @param id 处方 ID
 * @param patientId 就诊人 ID
 * @param status 处方状态
 */
public record ConsultationPrescriptionResourceRecord(Long id, Long patientId, String status) {
}
