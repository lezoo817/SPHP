package com.sphp.patient.registration.vo;

import lombok.Builder;
import lombok.Getter;

/**
 * 可选医院响应对象。
 */
@Getter
@Builder
public class HospitalListVO {

    /** 医院 ID */
    private final Long hospitalId;
    /** 医院名称 */
    private final String name;
    /** 医院等级 */
    private final String level;
    /** 医院地址 */
    private final String address;
    /** 医院联系方式 */
    private final String contact;
}
