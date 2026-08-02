package com.sphp.patient.health.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sphp.shared.entity.BaseDeleteDO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 患者过敏史实体。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("patient_allergy")
public class PatientAllergy extends BaseDeleteDO {

    /** 就诊人 ID */
    @TableField("patient_id")
    private Long patientId;
    /** 过敏原名称 */
    @TableField("allergen")
    private String allergen;
    /** 过敏反应描述 */
    @TableField("reaction")
    private String reaction;
    /** 过敏严重程度，本期接口不维护 */
    @TableField("severity")
    private String severity;
}
