package com.sphp.admin.doctor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 患者过敏史表实体（对应表 patient_allergy）。
 */
@Data
@TableName("patient_allergy")
public class PatientAllergy {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 患者 ID */
    private Long patientId;

    /** 过敏原 */
    private String allergen;

    /** 反应描述 */
    private String reaction;

    /** 严重程度：MILD / MODERATE / SEVERE */
    private String severity;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private OffsetDateTime deletedAt;
}