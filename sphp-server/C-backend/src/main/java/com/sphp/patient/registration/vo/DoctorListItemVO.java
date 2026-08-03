package com.sphp.patient.registration.vo;

import lombok.Builder;
import lombok.Getter;

/**
 * 医生列表响应项。
 */
@Getter
@Builder
public class DoctorListItemVO {

    /** 医生 ID */
    private final Long id;

    /** 医生姓名 */
    private final String name;

    /** 医生职称 */
    private final String title;

    /** 擅长领域 */
    private final String specialty;

    /** 挂号费，单位分 */
    private final Integer registrationFeeCent;

    /** 指定日期可约号源数量 */
    private final Long availableCount;

}
