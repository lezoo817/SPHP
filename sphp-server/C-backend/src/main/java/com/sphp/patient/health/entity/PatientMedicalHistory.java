package com.sphp.patient.health.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sphp.shared.entity.BaseDeleteDO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 患者既往史实体。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("patient_medical_history")
public class PatientMedicalHistory extends BaseDeleteDO {

    /** 就诊人 ID */
    @TableField("patient_id")
    private Long patientId;
    /** 既往史内容 */
    @TableField("content")
    private String content;
    /** 病史发生或记录日期 */
    @TableField("occurred_at")
    private LocalDate occurredAt;
}
