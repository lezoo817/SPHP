package com.sphp.admin.doctor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 患者表实体（对应表 patient）。
 *
 * @author lezoo17
 * @since 2026-08-10
 */
@Data
@TableName("patient")
public class Patient {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 姓名 */
    private String name;

    /** 身份证号（密文） */
    private String idCardCiphertext;

    /** 手机号（密文） */
    private String phoneCiphertext;

    /** 性别：MALE / FEMALE / UNKNOWN */
    private String gender;

    /** 出生日期 */
    private LocalDate dateOfBirth;

    /** 紧急联系人 */
    private String emergencyContact;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private OffsetDateTime deletedAt;
}