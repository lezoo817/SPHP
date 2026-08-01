package com.sphp.patient.family.vo;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 更新家庭成员响应对象。
 */
@Getter
@Builder
public class FamilyMemberUpdateVO {

    /** 就诊人 ID */
    private final Long patientId;
    /** 家庭成员姓名 */
    private final String name;
    /** 家庭关系编码 */
    private final String relation;
    /** 家庭关系中文名称 */
    private final String relationName;
    /** 性别编码 */
    private final String gender;
    /** 出生日期 */
    private final LocalDate birthday;
    /** 脱敏手机号 */
    private final String phone;
    /** 是否为默认就诊人 */
    private final Boolean isDefault;
    /** 更新时间 */
    private final OffsetDateTime updatedAt;
}
