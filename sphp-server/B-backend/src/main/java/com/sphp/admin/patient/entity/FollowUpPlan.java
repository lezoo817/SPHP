package com.sphp.admin.patient.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 随访计划表实体（对应表 follow_up_plan）。
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

    /** 随访类型 */
    private String followUpType;

    /** 随访内容 */
    private String content;

    /** 到期时间 */
    private OffsetDateTime dueAt;

    /** 提醒时间 */
    private OffsetDateTime remindAt;

    /** 状态：PENDING_CONFIRM / CONFIRMED / COMPLETED / CANCELLED */
    private String status;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private OffsetDateTime deletedAt;
}