package com.sphp.patient.consultation.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sphp.shared.entity.BaseDeleteDO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 处方查询实体。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("prescription")
public class ConsultationPrescription extends BaseDeleteDO {

    /** 关联问诊记录 ID */
    @TableField("consult_id")
    private Long consultationId;

    /** 开方医生 ID */
    @TableField("doctor_id")
    private Long doctorId;

    /** 就诊人 ID */
    @TableField("patient_id")
    private Long patientId;

    /** 处方状态 */
    @TableField("status")
    private String status;

    /** 开方时间 */
    @TableField("issued_at")
    private OffsetDateTime issuedAt;
}
