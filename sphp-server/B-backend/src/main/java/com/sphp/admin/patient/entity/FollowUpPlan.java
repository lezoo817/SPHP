package com.sphp.admin.patient.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 随访计划表实体（对应表 follow_up_plan）。
 *
 * @author lezoo17
 * @since 2026-08-09
 */
@Data
@TableName("follow_up_plan")
public class FollowUpPlan {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 患者 ID */
    private Long patientId;

    /** 挂号订单 ID */
    private Long appointmentId;

    /** 问诊记录 ID */
    private Long consultId;

    /** 随访类型（取值无数据库约束，未定义枚举字典） */
    private String followUpType;

    /** 随访内容 */
    private String content;

    /** 到期时间 */
    private OffsetDateTime dueAt;

    /** 提醒时间 */
    private OffsetDateTime remindAt;

    /** 状态：PENDING_CONFIRM / CONFIRMED / COMPLETED / CANCELLED */
    private String status;

    /** 创建时间 */
    private OffsetDateTime createdAt;

    /** 更新时间 */
    private OffsetDateTime updatedAt;

    /** 逻辑删除时间（软删除标志，null 表示未删除） */
    private OffsetDateTime deletedAt;
}