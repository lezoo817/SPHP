package com.sphp.admin.prescription.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 处方表实体（对应表 prescription）。
 */
@Data
@TableName("prescription")
public class Prescription {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 问诊记录 ID */
    private Long consultId;

    /** 医生 ID */
    private Long doctorId;

    /** 患者 ID */
    private Long patientId;

    /** 状态：DRAFT / SUBMITTED / APPROVED / REJECTED / CANCELLED */
    private String status;

    /** 驳回原因 */
    private String rejectReason;

    /** 签发时间 */
    private OffsetDateTime issuedAt;

    /** 审核时间 */
    private OffsetDateTime auditedAt;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private OffsetDateTime deletedAt;
}