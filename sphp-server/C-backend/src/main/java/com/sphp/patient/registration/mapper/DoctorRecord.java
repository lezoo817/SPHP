package com.sphp.patient.registration.mapper;

/**
 * C端医生列表查询记录。
 *
 * @param id 医生 ID
 * @param name 医生姓名
 * @param title 医生职称
 * @param specialty 擅长领域
 * @param registrationFeeCent 挂号费，单位分
 * @param availableCount 指定日期可约号源数
 */
public record DoctorRecord(Long id, String name, String title, String specialty,
                           Integer registrationFeeCent, Long availableCount) {
}
