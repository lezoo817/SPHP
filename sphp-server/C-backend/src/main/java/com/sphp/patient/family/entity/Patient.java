package com.sphp.patient.family.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sphp.shared.entity.BaseDeleteDO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 就诊人实体。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("patient")
public class Patient extends BaseDeleteDO {

    /** 就诊人姓名 */
    @TableField("name")
    private String name;

    /** 身份证号存储字段，接口层不得原样返回 */
    @TableField("id_card_ciphertext")
    private String idCardCiphertext;

    /** 手机号存储字段，接口层不得原样返回 */
    @TableField("phone_ciphertext")
    private String phoneCiphertext;

    /** 性别编码 */
    @TableField("gender")
    private String gender;

    /** 出生日期 */
    @TableField("date_of_birth")
    private LocalDate dateOfBirth;

    /** 紧急联系人 */
    @TableField("emergency_contact")
    private String emergencyContact;
}
