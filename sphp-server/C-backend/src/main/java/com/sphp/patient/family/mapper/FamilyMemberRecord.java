package com.sphp.patient.family.mapper;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 家庭成员联表查询记录。
 */
@Getter
@Setter
public class FamilyMemberRecord {

    /** 用户与就诊人关系 ID */
    private Long relationId;

    /** 就诊人 ID */
    private Long patientId;

    /** 就诊人姓名 */
    private String name;

    /** 关系编码 */
    private String relationship;

    /** 是否为默认就诊人 */
    private Boolean isDefault;

    /** 性别编码 */
    private String gender;

    /** 出生日期 */
    private LocalDate birthday;

    /** 手机号存储字段 */
    private String phone;

    /** 身份证号存储字段，仅限服务层去重校验 */
    private String idCardNo;

    /** 紧急联系人 */
    private String emergencyContact;

    /** 关系创建时间 */
    private OffsetDateTime createdAt;

    /** 关系更新时间 */
    private OffsetDateTime updatedAt;
}
