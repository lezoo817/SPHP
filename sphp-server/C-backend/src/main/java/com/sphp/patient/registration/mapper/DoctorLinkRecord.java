package com.sphp.patient.registration.mapper;

/**
 * 启用医生的医院归属查询记录。
 *
 * @param id 医生 ID
 * @param hospitalId 所属医院 ID
 */
public record DoctorLinkRecord(Long id, Long hospitalId) {
}
