package com.sphp.patient.health.vo;

import lombok.Builder;
import lombok.Getter;

/**
 * 健康档案展示的最小患者资料。
 */
@Getter
@Builder
public class HealthProfileVO {

    /** 就诊人 ID */
    private final Long id;

    /** 就诊人姓名 */
    private final String name;

    /** 性别编码 */
    private final String gender;

}
