package com.sphp.patient.consultation.mapper;

import java.time.OffsetDateTime;

/**
 * 已批准处方列表投影。
 *
 * @param id 处方 ID
 * @param consultationId 关联问诊 ID
 * @param doctorName 开方医生名称
 * @param issuedAt 开方时间
 */
public record PrescriptionListRecord(Long id, Long consultationId, String doctorName, OffsetDateTime issuedAt) {
}
