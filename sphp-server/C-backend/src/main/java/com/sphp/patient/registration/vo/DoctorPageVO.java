package com.sphp.patient.registration.vo;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 医生分页查询响应对象。
 */
@Getter
@Builder
public class DoctorPageVO {

    /** 当前页码 */
    private final int pageNo;
    /** 当前页大小 */
    private final int pageSize;
    /** 医生总数 */
    private final long total;
    /** 医生记录 */
    private final List<DoctorListItemVO> records;
}
