package com.sphp.admin.doctor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 患者既往史表实体（对应表 patient_medical_history）。
 *
 * @author lezoo17
 * @since 2026-08-10
 */
@Data
@TableName("patient_medical_history")
public class PatientMedicalHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 患者 ID */
    private Long patientId;

    /** 病史内容 */
    private String content;

    /** 发生日期 */
    private LocalDate occurredAt;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private OffsetDateTime deletedAt;
}