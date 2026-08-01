package com.sphp.patient.family.vo;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * 新增家庭成员响应对象。
 */
@Getter
@Builder
public class FamilyMemberCreateVO {

    /** 新建就诊人 ID */
    private final Long patientId;
    /** 家庭成员姓名 */
    private final String name;
    /** 家庭关系编码 */
    private final String relation;
    /** 是否为默认就诊人 */
    private final Boolean isDefault;
    /** 创建时间 */
    private final OffsetDateTime createdAt;
}
