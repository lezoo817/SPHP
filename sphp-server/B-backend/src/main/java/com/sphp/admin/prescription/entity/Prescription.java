package com.sphp.admin.prescription.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.sphp.admin.prescription.dto.RiskWarningVO;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 处方表实体（对应表 prescription）。
 *
 * @author lezoo17
 * @since 2026-08-09
 */
@Data
@TableName(value = "prescription", autoResultMap = true)
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

    /** 提交时命中的风险规则快照（JSONB），供审核展示与追溯 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<RiskWarningVO> riskWarnings;

    /** 签发时间 */
    private OffsetDateTime issuedAt;

    /** 审核时间 */
    private OffsetDateTime auditedAt;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private OffsetDateTime deletedAt;
}