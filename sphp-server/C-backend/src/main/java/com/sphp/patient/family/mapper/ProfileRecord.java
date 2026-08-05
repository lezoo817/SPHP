package com.sphp.patient.family.mapper;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 当前账号本人资料查询记录。
 */
@Getter
@Setter
public class ProfileRecord {

    /** 本人就诊人 ID */
    private Long patientId;
    /** 本人姓名 */
    private String name;
    /** 性别编码 */
    private String gender;
    /** 出生日期 */
    private LocalDate birthday;
    /** 手机号存储字段 */
    private String phone;
    /** 身份证号存储字段，仅限服务层脱敏返回 */
    private String idCardNo;
    /** 紧急联系人存储字段 */
    private String emergencyContact;
    /** 资料更新时间 */
    private OffsetDateTime updatedAt;
}
