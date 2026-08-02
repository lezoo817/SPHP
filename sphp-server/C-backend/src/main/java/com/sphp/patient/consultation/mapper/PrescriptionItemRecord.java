package com.sphp.patient.consultation.mapper;

/**
 * 处方药品明细投影。
 *
 * @param drugId 药品 ID
 * @param drugName 药品名称
 * @param specification 药品规格
 * @param dosage 单次剂量
 * @param frequency 用药频次
 * @param usage 用药方式
 * @param durationDays 用药天数
 */
public record PrescriptionItemRecord(Long drugId, String drugName, String specification, String dosage,
                                     String frequency, String usage, Short durationDays) {
}
