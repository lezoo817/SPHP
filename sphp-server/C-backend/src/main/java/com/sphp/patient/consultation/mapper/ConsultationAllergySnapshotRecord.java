package com.sphp.patient.consultation.mapper;

/**
 * 预问诊过敏史快照投影。
 *
 * @param allergen 过敏原
 * @param reaction 过敏反应描述
 */
public record ConsultationAllergySnapshotRecord(String allergen, String reaction) {
}
