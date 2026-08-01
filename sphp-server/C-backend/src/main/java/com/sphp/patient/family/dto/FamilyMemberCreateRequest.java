package com.sphp.patient.family.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 新增家庭成员请求。
 */
@Getter
@Setter
@NoArgsConstructor
public class FamilyMemberCreateRequest {

    /** 家庭成员姓名 */
    @NotBlank(message = "家庭成员姓名不能为空")
    @Size(max = 64, message = "家庭成员姓名长度不能超过64位")
    private String name;
    /** 家庭关系编码，服务层禁止 SELF */
    @NotBlank(message = "家庭关系不能为空")
    private String relation;
    /** 性别编码，可选 MALE、FEMALE、UNKNOWN */
    private String gender;
    /** 出生日期，不得晚于当天 */
    private LocalDate birthday;
    /** 家庭成员联系电话 */
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;
    /** 15 位或 18 位大陆居民身份证号 */
    @Pattern(regexp = "^(\\d{15}|\\d{17}[0-9Xx])$", message = "身份证号格式不正确")
    private String idCardNo;
    /** 紧急联系人及电话 */
    @Size(max = 256, message = "紧急联系人长度不能超过256位")
    private String emergencyContact;
}
