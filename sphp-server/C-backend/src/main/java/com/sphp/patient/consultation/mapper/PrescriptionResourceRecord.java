package com.sphp.patient.consultation.mapper;

/**
 * 处方归属与状态投影。
 *
 * @param id 处方 ID
 * @param patientId 就诊人 ID
 * @param status 处方状态
 */
public record PrescriptionResourceRecord(Long id, Long patientId, String status) {
}
