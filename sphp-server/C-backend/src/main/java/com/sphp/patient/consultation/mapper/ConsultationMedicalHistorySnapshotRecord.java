package com.sphp.patient.consultation.mapper;

import java.time.LocalDate;

/**
 * 预问诊既往史快照投影。
 *
 * @param content 既往史内容
 * @param occurredAt 病史发生或记录日期
 */
public record ConsultationMedicalHistorySnapshotRecord(String content, LocalDate occurredAt) {
}
