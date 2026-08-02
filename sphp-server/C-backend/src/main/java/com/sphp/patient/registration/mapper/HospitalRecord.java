package com.sphp.patient.registration.mapper;

/**
 * C端可选医院查询记录。
 *
 * @param hospitalId 医院 ID
 * @param name 医院名称
 * @param level 医院等级
 * @param address 医院地址
 * @param contact 医院联系方式
 */
public record HospitalRecord(Long hospitalId, String name, String level, String address, String contact) {
}
