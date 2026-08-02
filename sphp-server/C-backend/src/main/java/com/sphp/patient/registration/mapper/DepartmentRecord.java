package com.sphp.patient.registration.mapper;

/**
 * C端可选科室查询记录。
 *
 * @param id 科室 ID
 * @param name 科室名称
 * @param description 科室说明
 */
public record DepartmentRecord(Long id, String name, String description) {
}
