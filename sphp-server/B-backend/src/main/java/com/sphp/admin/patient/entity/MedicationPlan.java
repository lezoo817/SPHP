package com.sphp.admin.patient.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 用药计划表实体（对应表 medication_plan）。
 *
 * @author lezoo17
 * @since 2026-08-09
 */
@Data
@TableName("medication_plan")
public class MedicationPlan {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 患者 ID */
    private Long patientId;

    /** 处方明细 ID */
    private Long prescriptionItemId;

    /** 药品名称快照 */
    private String drugNameSnapshot;

    /** 频次（如 BID） */
    private String frequency;

    /** 用法（如 口服） */
    private String usageMethod;

    /** 用量（如 每次1片） */
    private String dosage;

    /** 用药天数 */
    private Integer durationDays;

    /** 下次提醒时间 */
    private OffsetDateTime nextRemindAt;

    /** 结束日期 */
    private LocalDate endAt;

    /** 状态：ACTIVE / PAUSED / COMPLETED */
    private String status;

    /** 创建时间 */
    private OffsetDateTime createdAt;

    /** 更新时间 */
    private OffsetDateTime updatedAt;

    /** 逻辑删除时间（软删除标志，null 表示未删除） */
    private OffsetDateTime deletedAt;
}