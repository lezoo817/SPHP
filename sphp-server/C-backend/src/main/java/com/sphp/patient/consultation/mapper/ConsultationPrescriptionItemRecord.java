package com.sphp.patient.consultation.mapper;

/**
 * 处方药品明细查询投影。
 *
 * @param drugId 药品 ID
 * @param drugName 药品名称
 * @param specification 药品规格
 * @param dosage 单次用量
 * @param frequency 用药频次
 * @param usage 用药方式
 * @param durationDays 用药天数
 */
public record ConsultationPrescriptionItemRecord(Long drugId, String drugName, String specification,
                                                 String dosage, String frequency, String usage,
                                                 Short durationDays) {
}
