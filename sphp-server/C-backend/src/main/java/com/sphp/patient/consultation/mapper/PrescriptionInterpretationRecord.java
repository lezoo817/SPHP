package com.sphp.patient.consultation.mapper;

import java.time.OffsetDateTime;

/**
 * 处方解读投影。
 *
 * @param prescriptionId 处方 ID
 * @param content 解读内容
 * @param disclaimer 免责声明
 * @param status 解读状态
 * @param generatedAt 生成时间
 */
public record PrescriptionInterpretationRecord(Long prescriptionId, String content, String disclaimer,
                                               String status, OffsetDateTime generatedAt) {
}
