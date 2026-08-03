package com.sphp.patient.family.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * C端当前账号本人资料更新请求。
 */
@Getter
@Setter
@NoArgsConstructor
public class ProfileUpdateRequest {

    /** 本人姓名 */
    @NotBlank(message = "姓名不能为空")
    @Size(max = 64, message = "姓名长度不能超过64位")
    private String name;
    /** 性别编码，可选 MALE、FEMALE、UNKNOWN */
    private String gender;
    /** 出生日期，不得晚于当天 */
    private LocalDate birthday;
    /** 联系电话，未传时保留原值 */
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;
    /** 紧急联系人及联系电话，未传时保留原值 */
    @Size(max = 256, message = "紧急联系人长度不能超过256位")
    private String emergencyContact;
}
