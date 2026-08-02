package com.sphp.patient.registration.mapper;

/**
 * 启用科室的医院归属查询记录。
 *
 * @param id 科室 ID
 * @param hospitalId 所属医院 ID
 */
public record DepartmentLinkRecord(Long id, Long hospitalId) {
}
