package com.sphp.patient.family.vo;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

/**
 * C端当前账号本人资料响应对象。
 */
@Getter
@Builder
public class ProfileVO {

    /** 本人就诊人 ID */
    private final Long id;

    /** 本人姓名 */
    private final String name;

    /** 性别编码 */
    private final String gender;

    /** 出生日期 */
    private final LocalDate birthday;

    /** 脱敏手机号 */
    private final String phone;

    /** 脱敏紧急联系人 */
    private final String emergencyContact;
}
