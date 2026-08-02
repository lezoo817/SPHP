package com.sphp.patient.health.mapper;

/**
 * 健康档案所需的最小患者资料查询记录。
 *
 * @param id 就诊人 ID
 * @param name 就诊人姓名
 * @param gender 性别编码
 */
public record HealthPatientProfileRecord(Long id, String name, String gender) {
}
