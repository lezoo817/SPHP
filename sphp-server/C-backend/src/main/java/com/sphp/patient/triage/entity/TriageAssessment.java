package com.sphp.patient.triage.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sphp.shared.entity.BaseDeleteDO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 导诊评估记录实体。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("triage_assessment")
public class TriageAssessment extends BaseDeleteDO {

    /** 就诊人 ID。 */
    @TableField("patient_id")
    private Long patientId;

    /** 评估时选定的医院 ID。 */
    @TableField("hospital_id")
    private Long hospitalId;

    /** 请求症状快照 JSON。 */
    @TableField("symptom_input")
    private String symptomInput;

    /** 规则计算出的最高紧急程度。 */
    private String urgency;

    /** 推荐科室快照 JSON。 */
    @TableField("recommended_departments")
    private String recommendedDepartments;
}
