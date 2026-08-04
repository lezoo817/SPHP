package com.sphp.patient.registration.vo;

import lombok.Builder;
import lombok.Getter;

/**
 * 可选科室响应对象。
 */
@Getter
@Builder
public class DepartmentListVO {

    /** 科室 ID */
    private final Long id;

    /** 科室名称 */
    private final String name;

    /** 科室位置 */
    private final String location;

}
